use java_bindings::exonum::crypto::{hash, Hash};
use java_bindings::jni::objects::{GlobalRef, JObject, JValue};
use java_bindings::jni::strings::JNIString;
use java_bindings::jni::sys::jsize;
use java_bindings::utils::unwrap_jni;
use java_bindings::{JniExecutor, MainExecutor, ServiceProxy};

use super::transaction::TRANSACTION_ADAPTER_CLASS;
use super::NATIVE_FACADE_CLASS;

pub const SERVICE_ADAPTER_CLASS: &str = "com/exonum/binding/service/adapters/UserServiceAdapter";
pub const SERVICE_MOCK_BUILDER_CLASS: &str =
    "com/exonum/binding/fakes/mocks/UserServiceAdapterMockBuilder";

pub const SERVICE_DEFAULT_ID: u16 = 42;
pub const SERVICE_DEFAULT_NAME: &str = "service 42";

pub struct ServiceMockBuilder {
    exec: MainExecutor,
    builder: GlobalRef,
}

impl ServiceMockBuilder {
    pub fn new(exec: MainExecutor) -> Self {
        let builder = unwrap_jni(exec.with_attached(|env| {
            let value = env.call_static_method(
                NATIVE_FACADE_CLASS,
                "createServiceFakeBuilder",
                format!("()L{};", SERVICE_MOCK_BUILDER_CLASS),
                &[],
            )?;
            env.new_global_ref(value.l()?)
        }));
        ServiceMockBuilder { exec, builder }
            .id(SERVICE_DEFAULT_ID)
            .name(SERVICE_DEFAULT_NAME)
            .state_hashes(&[hash(&[1, 2, 3])])
    }

    pub fn id(self, id: u16) -> Self {
        unwrap_jni(self.exec.with_attached(|env| {
            env.call_method(
                self.builder.as_obj(),
                "id",
                "(S)V",
                &[JValue::from(id as i16)],
            )?;
            Ok(())
        }));
        self
    }

    pub fn name<S>(self, name: S) -> Self
    where
        S: Into<JNIString>,
    {
        unwrap_jni(self.exec.with_attached(|env| {
            let name = env.new_string(name)?;
            env.call_method(
                self.builder.as_obj(),
                "name",
                "(Ljava/lang/String;)V",
                &[JValue::from(JObject::from(name))],
            )?;
            Ok(())
        }));
        self
    }

    #[cfg_attr(feature = "cargo-clippy", allow(needless_pass_by_value))]
    pub fn convert_transaction(self, transaction: GlobalRef) -> Self {
        unwrap_jni(self.exec.with_attached(|env| {
            env.call_method(
                self.builder.as_obj(),
                "convertTransaction",
                format!("(L{};)V", TRANSACTION_ADAPTER_CLASS),
                &[JValue::from(transaction.as_obj())],
            )?;
            Ok(())
        }));
        self
    }

    pub fn convert_transaction_throwing(self, exception_class: &str) -> Self {
        unwrap_jni(self.exec.with_attached(|env| {
            let exception = env.find_class(exception_class)?;
            env.call_method(
                self.builder.as_obj(),
                "convertTransactionThrowing",
                "(Ljava/lang/Class;)V",
                &[JValue::from(JObject::from(exception.into_inner()))],
            )?;
            Ok(())
        }));
        self
    }

    pub fn state_hashes(self, hashes: &[Hash]) -> Self {
        unwrap_jni(self.exec.with_attached(|env| {
            let byte_array_class = env.find_class("[B")?;
            let java_service_hashes =
                env.new_object_array(hashes.len() as jsize, byte_array_class, JObject::null())?;
            for (i, hash) in hashes.iter().enumerate() {
                let hash = JObject::from(env.byte_array_from_slice(hash.as_ref())?);
                env.set_object_array_element(java_service_hashes, i as jsize, hash)?;
            }
            env.call_method(
                self.builder.as_obj(),
                "stateHashes",
                "([[B)V",
                &[JValue::from(JObject::from(java_service_hashes))],
            )?;
            Ok(())
        }));
        self
    }

    pub fn initial_global_config<S>(self, config: S) -> Self
    where
        S: Into<Option<String>>,
    {
        unwrap_jni(self.exec.with_attached(|env| {
            let config = match config.into() {
                None => JObject::null(),
                Some(config) => env.new_string(config)?.into(),
            };
            env.call_method(
                self.builder.as_obj(),
                "initialGlobalConfig",
                "(Ljava/lang/String;)V",
                &[JValue::from(config)],
            )?;
            Ok(())
        }));
        self
    }

    pub fn initial_global_config_throwing(self, exception_class: &str) -> Self {
        unwrap_jni(self.exec.with_attached(|env| {
            let exception = env.find_class(exception_class)?;
            env.call_method(
                self.builder.as_obj(),
                "initialGlobalConfigThrowing",
                "(Ljava/lang/Class;)V",
                &[JValue::from(JObject::from(exception.into_inner()))],
            )?;
            Ok(())
        }));
        self
    }

    pub fn after_commit_throwing(self, exception_class: &str) -> Self {
        unwrap_jni(self.exec.with_attached(|env| {
            let exception = env.find_class(exception_class)?;
            env.call_method(
                self.builder.as_obj(),
                "afterCommitHandlerThrowing",
                "(Ljava/lang/Class;)V",
                &[JValue::from(JObject::from(exception.into_inner()))],
            )?;
            Ok(())
        }));
        self
    }

    pub fn get_mock_interaction_after_commit(self) -> (Self, GlobalRef) {
        let obj = unwrap_jni(self.exec.with_attached(|env| {
            let mock_interaction: JObject = env
                .call_method(
                    self.builder.as_obj(),
                    "getMockInteractionAfterCommit",
                    "()Lcom/exonum/binding/fakes/mocks/MockInteraction;",
                    &[],
                )?.l()?;
            Ok(env.new_global_ref(mock_interaction)?)
        }));
        (self, obj)
    }

    pub fn build(self) -> ServiceProxy {
        let (executor, service) = unwrap_jni(self.exec.clone().with_attached(|env| {
            let value = env.call_method(
                self.builder.as_obj(),
                "build",
                format!("()L{};", SERVICE_ADAPTER_CLASS),
                &[],
            )?;
            Ok((self.exec, env.new_global_ref(value.l()?)?))
        }));
        ServiceProxy::from_global_ref(executor, service)
    }
}
