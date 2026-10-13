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
if count < 1:
    raise SystemExit(
        f"No raw vkGetPhysicalDeviceFeatures2 calls matched in {source}; refusing to patch."
    )
source.write_text(text2, encoding="utf-8")
print(f"Applied Android Vulkan dynamic-dispatch fix to {count} call(s) in {source}")
