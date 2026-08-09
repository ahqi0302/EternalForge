EternalForge v3.3.3 Paper 1.21.10 Compatibility Fix

Compiled directly against the user-provided Paper 1.21.10-130 server JAR (Java 21).
Vault types are compile-only and are NOT packaged into the plugin JAR.
Fix target: org.bukkit.World is resolved from real Paper API, preventing class/interface ABI mismatch.
