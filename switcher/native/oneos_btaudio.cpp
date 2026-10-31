// oneos_btaudio: Bluetooth audio for a guest on a Galaxy that serves only
// Samsung's Bluetooth audio HAL.
//
// The SM8750 and older Galaxies (S25, S24, S23, Z Fold7, Z Flip7/5, A5x) register
// only vendor.samsung.hardware.bluetooth.audio.ISehBluetoothAudioProviderFactory,
// not Android's android.hardware.bluetooth.audio.IBluetoothAudioProviderFactory,
// so a guest's Bluetooth stack finds no audio HAL: devices connect and the sound
// stays on the phone speaker (0.4.32 reports: S24 Ultra, S25 Ultra, A57).
//
// Samsung's interface is Android's version 2 under other names (read from One
// UI's libbluetooth_jni.so, which has the Seh client stubs): the same
// transaction codes (factory 1-2, provider 1-6, port 1-7), the same SessionType,
// PcmConfiguration, PresentationPosition and LatencyMode, and the same
// android.hardware.audio.common metadata. Two differences: the port has an 8th
// method, sehGetAudioConfiguration, and the codec unions (CodecConfiguration.
// CodecSpecific, CodecCapabilities.Capabilities) have Samsung's SSC at tag 6,
// so Android's vendor (6) and opus (7) are Samsung's 7 and 8.
//
// This process registers Android's factory and forwards every call to
// Samsung's with the parcel copied as it is, under the other interface name:
//   factory 1 getProviderCapabilities: SSC entries dropped, tags 7/8 -> 6/7
//   factory 2 openProvider: the provider wrapped
//   provider 2 startSession: the guest's port wrapped (as Samsung's port, VINTF
//     stability so the vendor HAL may call it), codec tags 6/7 -> 7/8
//   provider 5 updateAudioConfiguration: codec tags 6/7 -> 7/8
//   port 8 sehGetAudioConfiguration: the configuration of the last start or update
// It answers version 2 for Android's interfaces, so the guest's stack never asks
// for the version 4 methods Samsung does not have.
//
// The guest's stack asks servicemanager AServiceManager_isDeclared() before it
// looks for the factory, so the session also declares the instance in the
// device manifest (oneos_switch.sh, ONEOS_GUEST_BT_AUDIO_BRIDGE).
//
// Usage: oneos_btaudio            serve (until killed)
//        oneos_btaudio --declared NAME   print 1 if servicemanager has NAME declared
//        oneos_btaudio --check     print what this phone has
//        oneos_btaudio --loop      a test bridge from Android's factory to itself (.../oneostest)
//        oneos_btaudio --client NAME   the first calls a guest's stack makes, against NAME
#include <android/binder_ibinder.h>
#include <android/binder_parcel.h>
#include <android/binder_status.h>
#include <android/log.h>
#include <dlfcn.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

#include <memory>
#include <mutex>
#include <vector>

namespace {

const char* kTag = "OneOSBtAudio";
const char* kAospFactoryName = "android.hardware.bluetooth.audio.IBluetoothAudioProviderFactory/default";
const char* kSehFactoryName = "vendor.samsung.hardware.bluetooth.audio.ISehBluetoothAudioProviderFactory/default";

const char* kAospFactory = "android.hardware.bluetooth.audio.IBluetoothAudioProviderFactory";
const char* kAospProvider = "android.hardware.bluetooth.audio.IBluetoothAudioProvider";
const char* kAospPort = "android.hardware.bluetooth.audio.IBluetoothAudioPort";
const char* kSehFactory = "vendor.samsung.hardware.bluetooth.audio.ISehBluetoothAudioProviderFactory";
const char* kSehProvider = "vendor.samsung.hardware.bluetooth.audio.ISehBluetoothAudioProvider";
const char* kSehPort = "vendor.samsung.hardware.bluetooth.audio.ISehBluetoothAudioPort";

// --loop (a test on a phone that has Android's HAL): the "Samsung" side is
// Android's own factory, codec tags are left as they are, and the bridge
// registers as .../oneostest, which nothing but --client uses.
bool gLoop = false;
const char* kLoopName = "android.hardware.bluetooth.audio.IBluetoothAudioProviderFactory/oneostest";

// android.hardware.bluetooth.audio version 2 (aidl_api/.../2/.hash): Samsung's
// interface matches it, so the guest's stack is told this one.
const int32_t kAospVersion = 2;
const char* kAospHash = "b82d5b9f717bbf63bef774ee8f72572065ca61ce";
// What One UI's BnSehBluetoothAudioPort answers.
const int32_t kSehPortVersion = 2;

const transaction_code_t kGetInterfaceVersion = FIRST_CALL_TRANSACTION + 16777214;
const transaction_code_t kGetInterfaceHash = FIRST_CALL_TRANSACTION + 16777213;

void logf(const char* fmt, ...) {
    va_list a, b;
    va_start(a, fmt);
    va_copy(b, a);
    __android_log_vprint(ANDROID_LOG_INFO, kTag, fmt, a);
    vfprintf(stderr, fmt, b);
    fputc('\n', stderr);
    va_end(b);
    va_end(a);
}

// Not in the NDK headers (LL-NDK / platform), but exported by every
// libbinder_ndk.so since Android 12.
struct Platform {
    AIBinder* (*checkService)(const char*) = nullptr;
    binder_exception_t (*addService)(AIBinder*, const char*) = nullptr;
    bool (*isDeclared)(const char*) = nullptr;
    void (*markVintf)(AIBinder*) = nullptr;
    bool (*setThreads)(uint32_t) = nullptr;
    void (*startPool)() = nullptr;
    void (*joinPool)() = nullptr;
    bool load() {
        void* h = dlopen("libbinder_ndk.so", RTLD_NOW);
        if (!h) return false;
        checkService = reinterpret_cast<decltype(checkService)>(dlsym(h, "AServiceManager_checkService"));
        addService = reinterpret_cast<decltype(addService)>(dlsym(h, "AServiceManager_addService"));
        isDeclared = reinterpret_cast<decltype(isDeclared)>(dlsym(h, "AServiceManager_isDeclared"));
        markVintf = reinterpret_cast<decltype(markVintf)>(dlsym(h, "AIBinder_markVintfStability"));
        setThreads = reinterpret_cast<decltype(setThreads)>(dlsym(h, "ABinderProcess_setThreadPoolMaxThreadCount"));
        startPool = reinterpret_cast<decltype(startPool)>(dlsym(h, "ABinderProcess_startThreadPool"));
        joinPool = reinterpret_cast<decltype(joinPool)>(dlsym(h, "ABinderProcess_joinThreadPool"));
        return checkService && addService && isDeclared && markVintf && setThreads && startPool && joinPool;
    }
} P;

AIBinder_Class* gAospFactoryCls;
AIBinder_Class* gAospProviderCls;
AIBinder_Class* gAospPortCls;  // client side: the guest's port
AIBinder_Class* gSehFactoryCls;  // client side
AIBinder_Class* gSehProviderCls;  // client side
AIBinder_Class* gSehPortCls;

AIBinder* gSehFactory;

// The configuration of a session, as Samsung's parcel (for sehGetAudioConfiguration).
struct Session {
    std::mutex m;
    AParcel* config = nullptr;  // owned
    ~Session() {
        if (config) AParcel_delete(config);
    }
};

struct ProviderData {
    AIBinder* seh;  // strong ref held
    std::shared_ptr<Session> session = std::make_shared<Session>();
};

struct PortData {
    AIBinder* aosp;  // the guest's IBluetoothAudioPort, strong ref held
    std::shared_ptr<Session> session;
};

// Android's codec tags -> Samsung's (CodecSpecific / Capabilities unions).
int32_t codecTagToSeh(int32_t t) { return !gLoop && t >= 6 ? t + 1 : t; }

binder_status_t writeOkStatus(AParcel* out) {
    AStatus* ok = AStatus_newOk();
    binder_status_t st = AParcel_writeStatusHeader(out, ok);
    AStatus_delete(ok);
    return st;
}

binder_status_t copyRest(const AParcel* in, AParcel* to, int32_t from) {
    int32_t end = AParcel_getDataSize(in);
    if (end <= from) return STATUS_OK;
    return AParcel_appendFrom(in, to, from, end - from);
}

// Sends `code` to `target` with the argument bytes of `in` from `from` on and
// copies the whole reply (status header included) into `out`.
binder_status_t forward(AIBinder* target, transaction_code_t code, const AParcel* in, int32_t from, AParcel* out) {
    AParcel* req = nullptr;
    binder_status_t st = AIBinder_prepareTransaction(target, &req);
    if (st != STATUS_OK) return st;
    st = copyRest(in, req, from);
    if (st != STATUS_OK) {
        AParcel_delete(req);
        return st;
    }
    AParcel* rep = nullptr;
    st = AIBinder_transact(target, code, &req, &rep, 0);
    if (st != STATUS_OK) return st;
    st = AParcel_appendFrom(rep, out, 0, AParcel_getDataSize(rep));
    AParcel_delete(rep);
    return st;
}

// Reads the header of a parcelable or union written as a value: the non-null
// marker, and for a union its tag, the member's marker and the member's size
// (a parcelable member starts with its size, which counts itself). Fills the
// extent of the whole value. One shape for an argument, a vector element and a
// union member: [1] [tag] [1] [size] fields...
struct Value {
    int32_t start = 0, end = 0;
    int32_t tag = -1;
    int32_t member = -1;  // where the member's size is, or -1 (null)
};
binder_status_t readUnionValue(const AParcel* p, Value* v) {
    v->start = AParcel_getDataPosition(p);
    int32_t marker = 0, inner = 0, size = 0;
    if (AParcel_readInt32(p, &marker) != STATUS_OK) return STATUS_BAD_VALUE;
    if (marker == 0) {  // null
        v->end = AParcel_getDataPosition(p);
        return STATUS_OK;
    }
    if (AParcel_readInt32(p, &v->tag) != STATUS_OK || AParcel_readInt32(p, &inner) != STATUS_OK) return STATUS_BAD_VALUE;
    if (inner == 0) {  // a null member
        v->end = AParcel_getDataPosition(p);
        return STATUS_OK;
    }
    v->member = AParcel_getDataPosition(p);
    if (AParcel_readInt32(p, &size) != STATUS_OK || size < 4) return STATUS_BAD_VALUE;
    v->end = v->member + size;
    if (v->end > AParcel_getDataSize(p)) return STATUS_BAD_VALUE;
    return AParcel_setDataPosition(p, v->end);
}

// Copies an AudioConfiguration from `in` (at its current position, which it
// leaves after the value) to `to`, Android's codec tags made Samsung's.
binder_status_t copyAudioConfiguration(const AParcel* in, AParcel* to) {
    Value v;
    binder_status_t st = readUnionValue(in, &v);
    if (st != STATUS_OK) return st;
    int32_t start = v.start, end = v.end, ps = v.member;
    // a2dpConfig: CodecConfiguration {size, codecType, encodedAudioBitrate,
    // peerMtu, isScmstEnabled, CodecSpecific config: [1] [tag] ...}
    if (v.tag == 1 && ps >= 0 && end - ps >= 28) {
        int32_t tagPos = ps + 24;
        AParcel_setDataPosition(in, tagPos);
        int32_t inner = 0;
        AParcel_readInt32(in, &inner);
        int32_t mapped = codecTagToSeh(inner);
        st = AParcel_appendFrom(in, to, start, tagPos - start);
        if (st == STATUS_OK) st = AParcel_writeInt32(to, mapped);
        if (st == STATUS_OK) st = AParcel_appendFrom(in, to, tagPos + 4, end - tagPos - 4);
    } else {
        st = AParcel_appendFrom(in, to, start, end - start);
    }
    AParcel_setDataPosition(in, end);
    return st;
}

void saveConfig(const std::shared_ptr<Session>& s, const AParcel* in, int32_t from) {
    AParcel* p = AParcel_create();
    AParcel_setDataPosition(in, from);
    if (copyAudioConfiguration(in, p) != STATUS_OK) {
        AParcel_delete(p);
        return;
    }
    std::lock_guard<std::mutex> l(s->m);
    if (s->config) AParcel_delete(s->config);
    s->config = p;
}

void* onCreatePass(void* args) { return args; }
void onDestroyNothing(void*) {}
binder_status_t onTransactNone(AIBinder*, transaction_code_t, const AParcel*, AParcel*) {
    return STATUS_UNKNOWN_TRANSACTION;
}

binder_status_t answerMeta(transaction_code_t code, AParcel* out, int32_t version, const char* hash) {
    binder_status_t st = writeOkStatus(out);
    if (st != STATUS_OK) return st;
    if (code == kGetInterfaceVersion) return AParcel_writeInt32(out, version);
    return AParcel_writeString(out, hash, strlen(hash));
}

// ---- Samsung's port, served to the vendor HAL for the guest's port ----

void portDestroy(void* d) {
    auto* pd = static_cast<PortData*>(d);
    AIBinder_decStrong(pd->aosp);
    delete pd;
}

binder_status_t portTransact(AIBinder* self, transaction_code_t code, const AParcel* in, AParcel* out) {
    auto* pd = static_cast<PortData*>(AIBinder_getUserData(self));
    if (code == kGetInterfaceVersion || code == kGetInterfaceHash)
        return answerMeta(code, out, kSehPortVersion, "notfrozen");
    if (code == FIRST_CALL_TRANSACTION + 7) {  // sehGetAudioConfiguration
        std::lock_guard<std::mutex> l(pd->session->m);
        if (!pd->session->config) return STATUS_FAILED_TRANSACTION;
        binder_status_t st = writeOkStatus(out);
        if (st != STATUS_OK) return st;
        return AParcel_appendFrom(pd->session->config, out, 0, AParcel_getDataSize(pd->session->config));
    }
    if (code < FIRST_CALL_TRANSACTION || code > FIRST_CALL_TRANSACTION + 6) return STATUS_UNKNOWN_TRANSACTION;
    return forward(pd->aosp, code, in, AParcel_getDataPosition(in), out);
}

// ---- Android's provider, served to the guest ----

void providerDestroy(void* d) {
    auto* pd = static_cast<ProviderData*>(d);
    AIBinder_decStrong(pd->seh);
    delete pd;
}

binder_status_t providerTransact(AIBinder* self, transaction_code_t code, const AParcel* in, AParcel* out) {
    auto* pd = static_cast<ProviderData*>(AIBinder_getUserData(self));
    if (code == kGetInterfaceVersion || code == kGetInterfaceHash)
        return answerMeta(code, out, kAospVersion, kAospHash);
    switch (code - FIRST_CALL_TRANSACTION) {
        case 0:  // endSession
        case 2:  // streamStarted
        case 3:  // streamSuspended
        case 5:  // setLowLatencyModeAllowed
            return forward(pd->seh, code, in, AParcel_getDataPosition(in), out);
        case 1: {  // startSession(port, AudioConfiguration, LatencyMode[])
            AIBinder* guestPort = nullptr;
            binder_status_t st = AParcel_readStrongBinder(in, &guestPort);
            if (st != STATUS_OK || !guestPort) return st != STATUS_OK ? st : STATUS_UNEXPECTED_NULL;
            if (!AIBinder_associateClass(guestPort, gAospPortCls)) {
                AIBinder_decStrong(guestPort);
                return STATUS_BAD_TYPE;
            }
            auto* portData = new PortData{guestPort, pd->session};  // takes the ref
            AIBinder* sehPort = AIBinder_new(gSehPortCls, portData);
            P.markVintf(sehPort);
            int32_t cfgPos = AParcel_getDataPosition(in);
            saveConfig(pd->session, in, cfgPos);
            AParcel_setDataPosition(in, cfgPos);

            AParcel* req = nullptr;
            st = AIBinder_prepareTransaction(pd->seh, &req);
            if (st == STATUS_OK) st = AParcel_writeStrongBinder(req, sehPort);
            AIBinder_decStrong(sehPort);  // the parcel holds it now
            if (st == STATUS_OK) st = copyAudioConfiguration(in, req);
            if (st == STATUS_OK) st = copyRest(in, req, AParcel_getDataPosition(in));
            if (st != STATUS_OK) {
                if (req) AParcel_delete(req);
                return st;
            }
            AParcel* rep = nullptr;
            st = AIBinder_transact(pd->seh, code, &req, &rep, 0);
            if (st != STATUS_OK) {
                logf("startSession: Samsung's provider failed: %d", st);
                return st;
            }
            st = AParcel_appendFrom(rep, out, 0, AParcel_getDataSize(rep));
            AParcel_delete(rep);
            logf("startSession forwarded");
            return st;
        }
        case 4: {  // updateAudioConfiguration(AudioConfiguration)
            int32_t cfgPos = AParcel_getDataPosition(in);
            saveConfig(pd->session, in, cfgPos);
            AParcel_setDataPosition(in, cfgPos);
            AParcel* req = nullptr;
            binder_status_t st = AIBinder_prepareTransaction(pd->seh, &req);
            if (st == STATUS_OK) st = copyAudioConfiguration(in, req);
            if (st != STATUS_OK) {
                if (req) AParcel_delete(req);
                return st;
            }
            AParcel* rep = nullptr;
            st = AIBinder_transact(pd->seh, code, &req, &rep, 0);
            if (st != STATUS_OK) return st;
            st = AParcel_appendFrom(rep, out, 0, AParcel_getDataSize(rep));
            AParcel_delete(rep);
            return st;
        }
        default:
            return STATUS_UNKNOWN_TRANSACTION;
    }
}

// ---- Android's factory, served to the guest ----

// getProviderCapabilities reply: Samsung's AudioCapabilities[] made Android's:
// SSC codec entries dropped, codec tags 7/8 -> 6/7, unknown union members dropped.
binder_status_t translateCapabilities(const AParcel* rep, AParcel* out) {
    AStatus* status = nullptr;
    binder_status_t st = AParcel_readStatusHeader(rep, &status);
    if (st != STATUS_OK) return st;
    bool ok = AStatus_isOk(status);
    AStatus_delete(status);
    if (!ok) return AParcel_appendFrom(rep, out, 0, AParcel_getDataSize(rep));
    int32_t n = 0;
    if (AParcel_readInt32(rep, &n) != STATUS_OK) return STATUS_BAD_VALUE;
    struct Elem { int32_t start, end, tagPos, tag; };
    std::vector<Elem> keep;
    for (int32_t i = 0; i < n; i++) {
        Value v;
        if (readUnionValue(rep, &v) != STATUS_OK) return STATUS_BAD_VALUE;
        if (v.tag > 2) continue;  // not one of Android's
        int32_t end = v.end, ps = v.member;
        Elem e{v.start, end, -1, 0};
        // a2dpCapabilities: CodecCapabilities {size, codecType, Capabilities: [1] [tag] ...}
        if (v.tag == 1 && ps >= 0 && end - ps >= 16) {
            e.tagPos = ps + 12;
            AParcel_setDataPosition(rep, e.tagPos);
            AParcel_readInt32(rep, &e.tag);
            AParcel_setDataPosition(rep, end);
            if (!gLoop) {
                if (e.tag == 6) continue;  // Samsung's SSC
                if (e.tag > 6) e.tag -= 1;
            }
        }
        keep.push_back(e);
    }
    st = writeOkStatus(out);
    if (st == STATUS_OK) st = AParcel_writeInt32(out, static_cast<int32_t>(keep.size()));
    for (const Elem& e : keep) {
        if (st != STATUS_OK) break;
        if (e.tagPos < 0) {
            st = AParcel_appendFrom(rep, out, e.start, e.end - e.start);
            continue;
        }
        st = AParcel_appendFrom(rep, out, e.start, e.tagPos - e.start);
        if (st == STATUS_OK) st = AParcel_writeInt32(out, e.tag);
        if (st == STATUS_OK) st = AParcel_appendFrom(rep, out, e.tagPos + 4, e.end - e.tagPos - 4);
    }
    if (n != static_cast<int32_t>(keep.size()))
        logf("capabilities: %d of Samsung's %d entries passed on", static_cast<int>(keep.size()), n);
    return st;
}

// A strong reference, released at the end of the scope.
struct Ref {
    AIBinder* b;
    ~Ref() {
        if (b) AIBinder_decStrong(b);
    }
};

// Samsung's factory, fetched again after its process (the audio HAL) died.
// The caller gets its own reference.
std::mutex gFactoryLock;
AIBinder* sehFactory() {
    std::lock_guard<std::mutex> l(gFactoryLock);
    if (!AIBinder_isAlive(gSehFactory)) {
        AIBinder* f = P.checkService(kSehFactoryName);
        if (f && AIBinder_associateClass(f, gSehFactoryCls)) {
            AIBinder_decStrong(gSehFactory);
            gSehFactory = f;
            logf("Samsung's factory fetched again (its process restarted)");
        } else if (f) {
            AIBinder_decStrong(f);
        }
    }
    AIBinder_incStrong(gSehFactory);  // the dead one if none came back: the call fails as it would have
    return gSehFactory;
}

binder_status_t factoryTransact(AIBinder*, transaction_code_t code, const AParcel* in, AParcel* out) {
    if (code == kGetInterfaceVersion || code == kGetInterfaceHash)
        return answerMeta(code, out, kAospVersion, kAospHash);
    Ref sehRef{sehFactory()};
    AIBinder* seh = sehRef.b;
    if (code == FIRST_CALL_TRANSACTION) {  // getProviderCapabilities(SessionType)
        AParcel* req = nullptr;
        binder_status_t st = AIBinder_prepareTransaction(seh, &req);
        if (st != STATUS_OK) return st;
        st = copyRest(in, req, AParcel_getDataPosition(in));
        if (st != STATUS_OK) {
            AParcel_delete(req);
            return st;
        }
        AParcel* rep = nullptr;
        st = AIBinder_transact(seh, code, &req, &rep, 0);
        if (st != STATUS_OK) return st;
        st = translateCapabilities(rep, out);
        AParcel_delete(rep);
        return st;
    }
    if (code == FIRST_CALL_TRANSACTION + 1) {  // openProvider(SessionType)
        int32_t sessionType = -1;
        int32_t pos = AParcel_getDataPosition(in);
        AParcel_readInt32(in, &sessionType);
        AParcel* req = nullptr;
        binder_status_t st = AIBinder_prepareTransaction(seh, &req);
        if (st != STATUS_OK) return st;
        st = copyRest(in, req, pos);
        if (st != STATUS_OK) {
            AParcel_delete(req);
            return st;
        }
        AParcel* rep = nullptr;
        st = AIBinder_transact(seh, code, &req, &rep, 0);
        if (st != STATUS_OK) return st;
        AStatus* status = nullptr;
        st = AParcel_readStatusHeader(rep, &status);
        if (st != STATUS_OK) {
            AParcel_delete(rep);
            return st;
        }
        if (!AStatus_isOk(status)) {
            const char* why = AStatus_getDescription(status);
            logf("openProvider(session type %d): Samsung's factory refused: %s", sessionType, why);
            AStatus_deleteDescription(why);
            AStatus_delete(status);
            st = AParcel_appendFrom(rep, out, 0, AParcel_getDataSize(rep));
            AParcel_delete(rep);
            return st;
        }
        AStatus_delete(status);
        AIBinder* prov = nullptr;
        st = AParcel_readStrongBinder(rep, &prov);
        AParcel_delete(rep);
        if (st != STATUS_OK) return st;
        AIBinder* mine = nullptr;
        if (prov) {
            if (!AIBinder_associateClass(prov, gSehProviderCls)) {
                AIBinder_decStrong(prov);
                return STATUS_BAD_TYPE;
            }
            mine = AIBinder_new(gAospProviderCls, new ProviderData{prov});
        }
        logf("openProvider(session type %d): %s", sessionType, prov ? "Samsung's provider" : "none");
        st = writeOkStatus(out);
        if (st == STATUS_OK) st = AParcel_writeStrongBinder(out, mine);
        if (mine) AIBinder_decStrong(mine);
        return st;
    }
    return STATUS_UNKNOWN_TRANSACTION;
}

void defineClasses() {
    gAospFactoryCls = AIBinder_Class_define(kAospFactory, onCreatePass, onDestroyNothing, factoryTransact);
    gAospProviderCls = AIBinder_Class_define(kAospProvider, onCreatePass, providerDestroy, providerTransact);
    gAospPortCls = AIBinder_Class_define(kAospPort, onCreatePass, onDestroyNothing, onTransactNone);
    gSehFactoryCls = AIBinder_Class_define(gLoop ? kAospFactory : kSehFactory, onCreatePass, onDestroyNothing, onTransactNone);
    gSehProviderCls = AIBinder_Class_define(gLoop ? kAospProvider : kSehProvider, onCreatePass, onDestroyNothing, onTransactNone);
    gSehPortCls = AIBinder_Class_define(gLoop ? kAospPort : kSehPort, onCreatePass, portDestroy, portTransact);
}

// --client NAME: what a guest's stack does first, against NAME.
int client(const char* name) {
    AIBinder* f = P.checkService(name);
    if (!f) {
        printf("no %s\n", name);
        return 1;
    }
    AIBinder_Class* fc = AIBinder_Class_define(kAospFactory, onCreatePass, onDestroyNothing, onTransactNone);
    AIBinder_Class* pc = AIBinder_Class_define(kAospProvider, onCreatePass, onDestroyNothing, onTransactNone);
    if (!AIBinder_associateClass(f, fc)) {
        printf("%s is not %s\n", name, kAospFactory);
        return 1;
    }
    auto call = [](AIBinder* b, transaction_code_t code, int32_t arg, bool hasArg, AParcel** rep) {
        AParcel* req = nullptr;
        binder_status_t st = AIBinder_prepareTransaction(b, &req);
        if (st == STATUS_OK && hasArg) st = AParcel_writeInt32(req, arg);
        if (st == STATUS_OK) st = AIBinder_transact(b, code, &req, rep, 0);
        if (st != STATUS_OK) return st;
        AStatus* s = nullptr;
        st = AParcel_readStatusHeader(*rep, &s);
        if (st == STATUS_OK && !AStatus_isOk(s)) st = STATUS_FAILED_TRANSACTION;
        AStatus_delete(s);
        return st;
    };
    AParcel* rep = nullptr;
    int32_t v = -1;
    if (call(f, kGetInterfaceVersion, 0, false, &rep) == STATUS_OK) AParcel_readInt32(rep, &v);
    if (rep) AParcel_delete(rep), rep = nullptr;
    printf("factory version %d\n", v);
    for (int32_t type : {1, 2}) {
        int32_t n = -1, tags[8] = {0};
        binder_status_t st = call(f, FIRST_CALL_TRANSACTION, type, true, &rep);
        if (st == STATUS_OK) {
            AParcel_readInt32(rep, &n);
            for (int32_t i = 0; i < n && i < 8; i++) {
                Value v;
                if (readUnionValue(rep, &v) != STATUS_OK) break;
                tags[i] = v.tag;
                if (v.tag == 1 && v.member >= 0) {  // the codec's tag, as 10 + tag
                    int32_t t = -1;
                    AParcel_setDataPosition(rep, v.member + 12);
                    AParcel_readInt32(rep, &t);
                    tags[i] = 10 + t;
                    AParcel_setDataPosition(rep, v.end);
                }
            }
        }
        if (rep) AParcel_delete(rep), rep = nullptr;
        printf("capabilities(session %d): status %d, %d entries, tags", type, st, n);
        for (int32_t i = 0; i < n && i < 8; i++) printf(" %d", tags[i]);
        printf("\n");
        AIBinder* prov = nullptr;
        st = call(f, FIRST_CALL_TRANSACTION + 1, type, true, &rep);
        if (st == STATUS_OK) AParcel_readStrongBinder(rep, &prov);
        if (rep) AParcel_delete(rep), rep = nullptr;
        int32_t pv = -1;
        if (prov && AIBinder_associateClass(prov, pc) && call(prov, kGetInterfaceVersion, 0, false, &rep) == STATUS_OK)
            AParcel_readInt32(rep, &pv);
        if (rep) AParcel_delete(rep), rep = nullptr;
        printf("openProvider(session %d): status %d, provider %s, version %d\n", type, st, prov ? "yes" : "no", pv);
        if (prov) AIBinder_decStrong(prov);
    }
    AIBinder_decStrong(f);
    return 0;
}

}  // namespace

int main(int argc, char** argv) {
    if (!P.load()) {
        logf("libbinder_ndk.so lacks the service manager calls");
        return 2;
    }
    if (argc == 3 && !strcmp(argv[1], "--declared")) {
        printf("%d\n", P.isDeclared(argv[2]) ? 1 : 0);
        return 0;
    }
    if (argc == 3 && !strcmp(argv[1], "--client")) return client(argv[2]);
    if (argc == 2 && !strcmp(argv[1], "--loop")) {
        gLoop = true;
        kSehFactoryName = kAospFactoryName;
        kAospFactoryName = kLoopName;
    }
    if (argc == 2 && !strcmp(argv[1], "--check")) {
        AIBinder* s = P.checkService(kSehFactoryName);
        AIBinder* a = P.checkService(kAospFactoryName);
        printf("samsung_factory=%d android_factory=%d android_declared=%d\n", s != nullptr, a != nullptr,
               P.isDeclared(kAospFactoryName) ? 1 : 0);
        if (s) AIBinder_decStrong(s);
        if (a) AIBinder_decStrong(a);
        return 0;
    }
    defineClasses();
    P.setThreads(4);
    P.startPool();
    for (int i = 0; i < 100 && !gSehFactory; i++) {
        gSehFactory = P.checkService(kSehFactoryName);
        if (!gSehFactory) usleep(100 * 1000);
    }
    if (!gSehFactory) {
        logf("no %s: nothing to bridge", kSehFactoryName);
        return 1;
    }
    if (!AIBinder_associateClass(gSehFactory, gSehFactoryCls)) {
        logf("%s is not Samsung's factory", kSehFactoryName);
        return 1;
    }
    AIBinder* factory = AIBinder_new(gAospFactoryCls, nullptr);
    binder_exception_t ex = P.addService(factory, kAospFactoryName);
    if (ex != EX_NONE) {
        logf("could not register %s: exception %d", kAospFactoryName, ex);
        return 1;
    }
    logf("serving %s for Samsung's factory (declared: %d)", kAospFactoryName, P.isDeclared(kAospFactoryName) ? 1 : 0);
    P.joinPool();
    return 0;
}
