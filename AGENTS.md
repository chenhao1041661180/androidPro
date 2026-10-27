# AndroidPro 项目约定

开发本项目时使用 `.agents/skills/androidpro-android-dev/SKILL.md`。项目使用 Kotlin、XML/ViewBinding、MVVM、StateFlow/Flow、Paging 3、OkHttp/Retrofit；所有 Gradle 操作必须通过 `./gradlew`。

业务代码按 feature 放在 `app/src/main/java/com/example/androidpro/feature/`。公共 UI、网络和跨业务契约分别放在 `core/ui`、`core/network`、`core/common`，通用列表框架（Paging3 + Room + RemoteMediator）放在 `core/list`；禁止让 core 模块依赖 app。
