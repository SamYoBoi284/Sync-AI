#!/usr/bin/env python3
"""Apply the Android Vulkan-Hpp dynamic-dispatch compatibility fix to llama.cpp."""
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_llama_vulkan_android.py <llama-source-dir>")

source = Path(sys.argv[1]) / "ggml/src/ggml-vulkan/ggml-vulkan.cpp"
text = source.read_text(encoding="utf-8")
pattern = re.compile(
    r"(?m)^([ \t]*)vkGetPhysicalDeviceFeatures2\(device->physical_device,\s*&device_features2\);"
)
replacement = (
    r"\1VULKAN_HPP_DEFAULT_DISPATCHER."
    r"vkGetPhysicalDeviceFeatures2(device->physical_device, &device_features2);"
)
text2, count = pattern.subn(replacement, text)

if count:
    source.write_text(text2, encoding="utf-8")
    print(f"Applied Android Vulkan dynamic-dispatch fix to {count} call(s) in {source}")
else:
    # CMake may invoke this more than once when Gradle reconfigures native code.
    already_patched = text.count(
        "VULKAN_HPP_DEFAULT_DISPATCHER.vkGetPhysicalDeviceFeatures2("
    )
    if already_patched < 1:
        raise SystemExit(
            f"No raw calls or previously patched calls found in {source}; refusing to continue."
        )
    print(f"Android Vulkan dynamic-dispatch fix already present at {already_patched} call(s) in {source}")
