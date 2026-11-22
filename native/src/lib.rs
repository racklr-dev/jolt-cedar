// C ABI shim over cedar-policy's JSON entry points, so jolt.ffi can bind
// the policy engine without JVM/JNI. Every function takes/returns JSON strings;
// the caller frees returned strings with cedar_free_string.
use std::ffi::{CStr, CString};
use std::os::raw::c_char;
use serde_json::{json, Value};

fn cs(p: *const c_char) -> String {
    if p.is_null() {
        String::new()
    } else {
        unsafe { CStr::from_ptr(p) }.to_string_lossy().into_owned()
    }
}

fn out(s: String) -> *mut c_char {
    CString::new(s).unwrap_or_else(|_| CString::new("").unwrap()).into_raw()
}

/// is_authorized_json_str(request_json, policies, entities) -> decision JSON.
/// request_json: {"principal": {"type":..,"id":..}, "action": {...},
///   "resource": {...}, "context": {...}}
/// policies: cedar policy source text.
/// entities: a cedar entities JSON array.
#[no_mangle]
pub unsafe extern "C" fn is_authorized_json_str(
    request: *const c_char,
    policies: *const c_char,
    entities: *const c_char,
) -> *mut c_char {
    let req: Value = serde_json::from_str(&cs(request)).unwrap_or(json!({}));
    let ents: Value = serde_json::from_str(&cs(entities)).unwrap_or(json!([]));
    let call = json!({
        "principal": req.get("principal").cloned().unwrap_or(json!({})),
        "action": req.get("action").cloned().unwrap_or(json!({})),
        "resource": req.get("resource").cloned().unwrap_or(json!({})),
        "context": req.get("context").cloned().unwrap_or(json!({})),
        "policies": cs(policies),
        "entities": ents,
    });
    match cedar_policy::ffi::is_authorized_json_str(&call.to_string()) {
        Ok(ans) => out(ans),
        Err(e) => out(json!({ "error": e.to_string() }).to_string()),
    }
}

/// validate_json_str(policies, schema) -> validation JSON.
#[no_mangle]
pub unsafe extern "C" fn validate_json_str(
    policies: *const c_char,
    schema: *const c_char,
) -> *mut c_char {
    let sch: Value = serde_json::from_str(&cs(schema)).unwrap_or(json!({}));
    let call = json!({ "schema": sch, "policies": cs(policies) });
    match cedar_policy::ffi::validate_json_str(&call.to_string()) {
        Ok(ans) => out(ans),
        Err(e) => out(json!({ "error": e.to_string() }).to_string()),
    }
}

/// Free a string returned by is_authorized_json_str / validate_json_str.
#[no_mangle]
pub unsafe extern "C" fn cedar_free_string(s: *mut c_char) {
    if !s.is_null() {
        drop(CString::from_raw(s));
    }
}
