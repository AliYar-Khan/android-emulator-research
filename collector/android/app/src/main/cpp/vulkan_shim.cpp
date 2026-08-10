// Read-only Vulkan enumeration shim.
//
// Loads libvulkan.so (present on any Android device with a Vulkan
// implementation; the loader itself is installed since API 24) and exposes a
// small JNI surface returning structured strings. This performs only
// enumeration queries - no rendering, no writes, no privileged calls.
//
// String encoding convention: multi-field records are '|'-separated; field
// names of Vulkan objects never contain '|'.
//
// Return null (JNI) on any failure so Kotlin can distinguish:
//   unavailable          - libvulkan could not be loaded / no physical devices
//   failed_to_initialize - loader present but instance creation failed

#include <jni.h>
#include <dlfcn.h>

#include <string>
#include <vector>

#include <vulkan/vulkan.h>

namespace {

struct VulkanApi {
  PFN_vkEnumerateInstanceVersion enumerate_instance_version = nullptr;
  PFN_vkEnumerateInstanceLayerProperties enumerate_instance_layers = nullptr;
  PFN_vkEnumerateInstanceExtensionProperties enumerate_instance_extensions = nullptr;
  PFN_vkCreateInstance create_instance = nullptr;
  PFN_vkEnumeratePhysicalDevices enumerate_physical_devices = nullptr;
  PFN_vkGetPhysicalDeviceProperties get_physical_device_properties = nullptr;
  PFN_vkEnumerateDeviceExtensionProperties enumerate_device_extensions = nullptr;
  PFN_vkGetPhysicalDeviceQueueFamilyProperties get_queue_family_properties = nullptr;
};

VulkanApi& api() {
  static VulkanApi instance;
  static bool initialized = false;
  if (!initialized) {
    void* handle = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (handle != nullptr) {
      instance.enumerate_instance_version =
          reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
              dlsym(handle, "vkEnumerateInstanceVersion"));
      instance.enumerate_instance_layers =
          reinterpret_cast<PFN_vkEnumerateInstanceLayerProperties>(
              dlsym(handle, "vkEnumerateInstanceLayerProperties"));
      instance.enumerate_instance_extensions =
          reinterpret_cast<PFN_vkEnumerateInstanceExtensionProperties>(
              dlsym(handle, "vkEnumerateInstanceExtensionProperties"));
      instance.create_instance =
          reinterpret_cast<PFN_vkCreateInstance>(dlsym(handle, "vkCreateInstance"));
      instance.enumerate_physical_devices =
          reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(
              dlsym(handle, "vkEnumeratePhysicalDevices"));
      instance.get_physical_device_properties =
          reinterpret_cast<PFN_vkGetPhysicalDeviceProperties>(
              dlsym(handle, "vkGetPhysicalDeviceProperties"));
      instance.enumerate_device_extensions =
          reinterpret_cast<PFN_vkEnumerateDeviceExtensionProperties>(
              dlsym(handle, "vkEnumerateDeviceExtensionProperties"));
      instance.get_queue_family_properties =
          reinterpret_cast<PFN_vkGetPhysicalDeviceQueueFamilyProperties>(
              dlsym(handle, "vkGetPhysicalDeviceQueueFamilyProperties"));
    }
    initialized = true;
  }
  return instance;
}

bool has_loader() {
  return api().create_instance != nullptr &&
         api().enumerate_physical_devices != nullptr;
}

// Creates a bare instance (no layers, no extensions) for enumeration only.
// Returns VK_NULL_HANDLE on failure.
VkInstance create_enumeration_instance() {
  VkApplicationInfo app_info{};
  app_info.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
  app_info.pApplicationName = "pubg-compat-research-collector";
  app_info.applicationVersion = 1;
  app_info.pEngineName = "pubg-compat-research";
  app_info.engineVersion = 1;
  app_info.apiVersion = VK_API_VERSION_1_0;

  VkInstanceCreateInfo create_info{};
  create_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
  create_info.pApplicationInfo = &app_info;

  VkInstance instance = VK_NULL_HANDLE;
  VkResult result = api().create_instance(&create_info, nullptr, &instance);
  return result == VK_SUCCESS ? instance : VK_NULL_HANDLE;
}

std::vector<VkPhysicalDevice> enumerate_devices(VkInstance instance) {
  uint32_t count = 0;
  if (api().enumerate_physical_devices(instance, &count, nullptr) != VK_SUCCESS) {
    return {};
  }
  std::vector<VkPhysicalDevice> devices(count);
  if (api().enumerate_physical_devices(instance, &count, devices.data()) != VK_SUCCESS) {
    return {};
  }
  return devices;
}

jobjectArray to_string_array(JNIEnv* env, const std::vector<std::string>& values) {
  jclass string_class = env->FindClass("java/lang/String");
  jobjectArray array = env->NewObjectArray(
      static_cast<jsize>(values.size()), string_class, nullptr);
  for (jsize i = 0; i < static_cast<jsize>(values.size()); ++i) {
    env->SetObjectArrayElement(array, i, env->NewStringUTF(values[i].c_str()));
  }
  return array;
}

std::string format_version(uint32_t version) {
  return std::to_string(VK_VERSION_MAJOR(version)) + "." +
         std::to_string(VK_VERSION_MINOR(version)) + "." +
         std::to_string(VK_VERSION_PATCH(version));
}

std::string format_driver_version(uint32_t version) {
  // Android Vulkan driver version encoding (VK_KHR_driver_properties).
  return std::to_string(VK_VERSION_MAJOR(version)) + "." +
         std::to_string(VK_VERSION_MINOR(version)) + "." +
         std::to_string(VK_VERSION_PATCH(version));
}

const char* device_type_name(VkPhysicalDeviceType type) {
  switch (type) {
    case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU: return "integrated_gpu";
    case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU: return "discrete_gpu";
    case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU: return "virtual_gpu";
    case VK_PHYSICAL_DEVICE_TYPE_CPU: return "cpu";
    default: return "other";
  }
}

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_pubgcompat_collector_collectors_VulkanShim_apiVersion(JNIEnv* env, jobject) {
  if (!has_loader()) return nullptr;
  uint32_t version = VK_API_VERSION_1_0;
  if (api().enumerate_instance_version != nullptr) {
    api().enumerate_instance_version(&version);
  }
  return env->NewStringUTF(format_version(version).c_str());
}

JNIEXPORT jobjectArray JNICALL
Java_com_pubgcompat_collector_collectors_VulkanShim_instanceLayers(JNIEnv* env, jobject) {
  if (!has_loader()) return nullptr;
  uint32_t count = 0;
  if (api().enumerate_instance_layers(&count, nullptr) != VK_SUCCESS) return nullptr;
  std::vector<VkLayerProperties> layers(count);
  if (api().enumerate_instance_layers(&count, layers.data()) != VK_SUCCESS) {
    return nullptr;
  }
  std::vector<std::string> out;
  out.reserve(layers.size());
  for (const auto& layer : layers) {
    out.emplace_back(std::string(layer.layerName) + ":" +
                     std::to_string(layer.implementationVersion) + ":" +
                     format_version(layer.specVersion));
  }
  return to_string_array(env, out);
}

JNIEXPORT jobjectArray JNICALL
Java_com_pubgcompat_collector_collectors_VulkanShim_instanceExtensions(JNIEnv* env, jobject) {
  if (!has_loader()) return nullptr;
  uint32_t count = 0;
  if (api().enumerate_instance_extensions(nullptr, &count, nullptr) != VK_SUCCESS) {
    return nullptr;
  }
  std::vector<VkExtensionProperties> extensions(count);
  if (api().enumerate_instance_extensions(nullptr, &count, extensions.data()) != VK_SUCCESS) {
    return nullptr;
  }
  std::vector<std::string> out;
  out.reserve(extensions.size());
  for (const auto& ext : extensions) {
    out.emplace_back(std::string(ext.extensionName) + ":" + std::to_string(ext.specVersion));
  }
  return to_string_array(env, out);
}

JNIEXPORT jobjectArray JNICALL
Java_com_pubgcompat_collector_collectors_VulkanShim_physicalDevices(JNIEnv* env, jobject) {
  if (!has_loader()) return nullptr;
  VkInstance instance = create_enumeration_instance();
  if (instance == VK_NULL_HANDLE) return nullptr;
  auto devices = enumerate_devices(instance);
  if (devices.empty()) return nullptr;

  std::vector<std::string> out;
  out.reserve(devices.size());
  for (size_t i = 0; i < devices.size(); ++i) {
    VkPhysicalDeviceProperties props{};
    api().get_physical_device_properties(devices[i], &props);
    out.emplace_back(
        std::string(props.deviceName) + "|" +
        std::to_string(props.vendorID) + "|" +
        std::to_string(props.deviceID) + "|" +
        std::to_string(props.driverVersion) + "|" +
        format_version(props.apiVersion) + "|" +
        device_type_name(props.deviceType) + "|" +
        format_driver_version(props.driverVersion));
  }
  return to_string_array(env, out);
}

JNIEXPORT jobjectArray JNICALL
Java_com_pubgcompat_collector_collectors_VulkanShim_deviceExtensions(
    JNIEnv* env, jobject, jint device_index) {
  if (!has_loader()) return nullptr;
  VkInstance instance = create_enumeration_instance();
  if (instance == VK_NULL_HANDLE) return nullptr;
  auto devices = enumerate_devices(instance);
  if (device_index < 0 || static_cast<size_t>(device_index) >= devices.size()) {
    return nullptr;
  }

  uint32_t count = 0;
  if (api().enumerate_device_extensions(devices[device_index], nullptr, &count, nullptr) !=
      VK_SUCCESS) {
    return nullptr;
  }
  std::vector<VkExtensionProperties> extensions(count);
  if (api().enumerate_device_extensions(devices[device_index], nullptr, &count,
                                        extensions.data()) != VK_SUCCESS) {
    return nullptr;
  }
  std::vector<std::string> out;
  out.reserve(extensions.size());
  for (const auto& ext : extensions) {
    out.emplace_back(std::string(ext.extensionName) + ":" + std::to_string(ext.specVersion));
  }
  return to_string_array(env, out);
}

JNIEXPORT jobjectArray JNICALL
Java_com_pubgcompat_collector_collectors_VulkanShim_queueFamilies(
    JNIEnv* env, jobject, jint device_index) {
  if (!has_loader()) return nullptr;
  VkInstance instance = create_enumeration_instance();
  if (instance == VK_NULL_HANDLE) return nullptr;
  auto devices = enumerate_devices(instance);
  if (device_index < 0 || static_cast<size_t>(device_index) >= devices.size()) {
    return nullptr;
  }

  uint32_t count = 0;
  api().get_queue_family_properties(devices[device_index], &count, nullptr);
  std::vector<VkQueueFamilyProperties> families(count);
  api().get_queue_family_properties(devices[device_index], &count, families.data());

  std::vector<std::string> out;
  out.reserve(families.size());
  for (const auto& family : families) {
    out.emplace_back(
        std::to_string(family.queueCount) + "|" +
        std::to_string(family.queueFlags) + "|" +
        std::to_string(family.timestampValidBits) + "|" +
        std::to_string(family.minImageTransferGranularity.width) + "|" +
        std::to_string(family.minImageTransferGranularity.height) + "|" +
        std::to_string(family.minImageTransferGranularity.depth));
  }
  return to_string_array(env, out);
}

}  // extern "C"
