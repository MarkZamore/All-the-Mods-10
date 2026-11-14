# Pack-owned native client policies

The two runtime scripts are ordinary KubeJS client scripts. They run before the
initial game menu, independently of LANMinecraft's launch code. They use mutable
public configuration fields from the exact shipped native mods; no JAR is patched.

- `lanmc_draconic_hud_compat.js` sets only ImmediatelyFast `hud_batching=false` in
  its loaded configuration and runtime configuration. This is the official ATM10
  8.4 known-issue workaround. Draconic's 3D HUD and visual effects remain enabled;
  ordinary HUD submission may use more draw calls than batching.
- `lanmc_memory_warning_policy.js` sets only Memory Settings' loaded
  `disableWarnings=true` before its initial game-menu panel check. This prevents
  the upper-memory panel for a heap above the author's 32000 MB recommendation.
  It does not impose a heap limit.

Memory Settings checks the client heap before KubeJS runs. The pack sets only
`minimumClient.minimumClient` to zero in `config/memorysettings.json` so the
early allocation recommendation does not appear. The mod's 32-bit Java check,
server thresholds, and the launcher's own heap recommendations keep their
existing behavior. Updates back up a displaced instance config through the
existing pack-owned files mechanism.

Neither script writes a personal JSON file or resets unrelated options. The
pack's existing in-menu policy still runs on every launch.

After importing a later author pack, keep this directory and run:

```text
python tools/client-policy/prepare.py --pack . --apply
```

The tool checks the native script APIs and the reviewed early-warning bytecode.
It restores missing scripts from pinned templates and sets the client threshold
in the prepared source config, preserving its other bytes. Without `--apply`,
the check refuses to proceed if an author update reintroduced the threshold.
Changes to the early-warning bytecode require review before publishing. CI
runs this before manifest generation.

The source tests cover rejection of missing native members, restoration of
owned scripts, the exact client threshold change and idempotence.
`native-proof.json` records the earlier Rhino check and loader ordering for
the in-menu policy.

`EarlyMemoryWarningProbe.java` takes absolute paths to the original and
prepared Memory Settings JSON files. Run with 64-bit Java 21 in a fresh
directory with the exact NeoForge loader, Memory Settings, Cupboard, Gson and
logging libraries on the classpath. Omit LWJGL TinyFileDialogs so an attempted
dialog can be observed without opening a window. The probe compares the real
Cupboard-loaded configs and tests both the allocation branch and the independent
Java architecture warning.

## Reproduce the standalone native check

`NativePolicyProbe.java` is source-only and takes the prepared pack root as its
single argument. Run Java 21+ with a classpath from the exact pack's native client
runtime plus its mod JARs, in a fresh separate empty working directory. For long
Windows classpaths use Java's standard `@arguments.txt` file. Include the current
Memory Settings/Cupboard, ImmediatelyFast and Rhino JARs, NeoForge's named client
and loader/libraries. Do not substitute mock configuration classes.

```text
java --class-path <EXACT_NATIVE_RUNTIME_AND_MOD_JARS> <PACK>/tools/client-policy/NativePolicyProbe.java <PACK>
```

The probe refuses a working directory containing a pack or existing fixture
config files. It copies only the author's memory-settings JSON into that fresh
fixture, initializes native FML config paths there, evaluates the two actual pack
scripts with real Rhino NativeJavaClass handling, and checks changed fields,
idempotence, native runtime recreation and both JSON files' byte preservation.
Its small `Java.loadClass` fixture binding delegates to actual native Rhino/Java
classes; it is not a substitute KubeJS lifecycle or renderer. Actual load order is
reviewed separately in `native-proof.json`. No private classpath, logs or paths
are committed.
