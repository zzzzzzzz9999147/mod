# Magic Induction — Gradle 构建配置导出

导出自 `magic-induction` 工程（版本 **1.9.14**），2026-11-12。

这个包里是**完整的 Gradle 构建配置**，不含 `src/` 源码。把它放到工程根目录覆盖同名文件，
就能直接 `./gradlew build` 出一个可用的 jar。

## 文件清单

| 文件 | 作用 |
|---|---|
| `build.gradle` | 主构建脚本：ForgeGradle 6.0 插件、依赖、run 配置、资源变量替换、jar 清单 |
| `settings.gradle` | 插件仓库（Gradle Plugin Portal + MinecraftForge maven）与工程名 |
| `gradle.properties` | 全部版本号与 mod 元信息（MC 1.20.1 / Forge 47.4.28 / mod_version 1.9.14） |
| `gradlew` / `gradlew.bat` | Gradle 8.8 启动脚本 |
| `gradle/wrapper/gradle-wrapper.properties` | 指定 Gradle 8.8 |
| `gradle/wrapper/gradle-wrapper.jar` | wrapper 引导 jar |

## 关键配置要点

**JDK 必须 17**（`java.toolchain.languageVersion = JavaLanguageVersion.of(17)`）。JDK 21 会在 ForgeGradle 的
`deobf` 阶段报 `Unsupported class file major version 65`。

**映射用 `official`**，不是 `official_1.20.1` 也不是 `parchment`。改成别的会让下面那条 mixin 参数失效，
并且 Create 的 SRG 名解析不到。

**Create 的三个依赖全是 `compileOnly`**：

```gradle
compileOnly fg.deobf('maven.modrinth:create:mc1.20.1-6.0.8')
compileOnly "net.createmod.ponder:Ponder-Forge-1.20.1:1.0.92"
compileOnly "dev.engine-room.flywheel:flywheel-forge-1.20.1:1.0.1"
```

- jar 里**不会**带上任何 Create 的类，没装 Create 的整合包照常启动；
- 但 javac 必须能解析到它们 —— Create 6.x 把 Ponder 拆成了独立库，
  而且 Ponder 的 pom 没把 Flywheel 声明成传递依赖，所以三个都得自己写；
- 少了 Ponder 会报 `class file for net.createmod.ponder.api.VirtualBlockEntity not found`，
  少了 Flywheel 会报 `cannot access PartialModel`。

**mixin 重映射参数挂在 `runs.configureEach` 上**，不是 `tasks.withType(JavaExec)`：

```gradle
jvmArgs '-Dmixin.env.remapRefMap=true',
        "-Dmixin.env.refMapRemappingFile=${projectDir}/build/createSrgToMcp/output.srg"
```

只有挂在这里才会真正进 JVM 参数。丢掉这两行，装了 Create / Flywheel 的 `runClient`
会在 Mixin Apply 阶段 FATAL（`@Shadow field f_91036_ was not located`）。

**仓库要加 `maven.createmod.net`**，并且用 `content { includeGroup ... }` 收窄范围，
否则会影响其他依赖的解析速度。

**`mods.toml` 的变量来自 `processResources` 的 `expand`**，所以在 `gradle.properties` 里改
`mod_version` 会自动进 jar 的元信息，不需要手动同步两处。

## 常用命令

```bash
./gradlew build            # 出 jar 到 build/libs/magicinduction-<version>.jar
./gradlew runClient        # 开发客户端（需要能开图形窗口的环境）
./gradlew runServer        # 开发服务端，--nogui
./gradlew runData          # 跑 DataGenerator，产物进 src/generated/resources
```

## 已知限制（照原样记录，不是配置问题）

装了 Create 的情况下 `./gradlew runClient` **会在进入世界时崩溃**：

```
@Shadow field f_91036_ was not located in the target class net.minecraft.client.Minecraft
Using refmap flywheel.refmap.json
```

原因是 Flywheel 1.0.x 的 `flywheel.refmap.json` 只有 4 条映射，不含 `f_91036_`，
1.0.0 到 1.0.6 所有版本都这样（它假定只在生产环境跑）。上面的 mixin 参数开着也一样崩，
属于上游发布缺陷，不是本工程的配置错误。要验证渲染效果，只能把 Create 从 `run/mods/`
挪走跑，或者用正式客户端测。
