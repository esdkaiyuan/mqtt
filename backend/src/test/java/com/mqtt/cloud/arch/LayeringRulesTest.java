package com.mqtt.cloud.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * 分层与边界规则。
 * <p>
 * 把架构约定固化为可执行断言，避免后续改动（尤其是热路径上的"图省事直连"）悄悄回退边界。
 * 规则失败只允许两种处理：修正越界代码，或调整规则使其反映真实架构 —— <b>不得用 @ArchIgnore 绕过</b>。
 */
@AnalyzeClasses(packages = "com.mqtt.cloud", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringRulesTest {

    /**
     * 分层依赖单向：Controller → Service → Mapper。
     * <p>
     * 与计划稿相比，这里显式允许<b>同层互调</b>（Service 访问 Service、Mapper 访问 Mapper）：
     * Service 之间编排（如 {@code EmqxAuthServiceImpl} 依赖 {@code ProductService} / {@code DeviceService}）是
     * 分层架构的常规形态，若不允许会产生大量伪违规。真正要禁止的是<b>反向</b>依赖 ——
     * Mapper 不得访问 Service、任何层不得访问 Controller。
     */
    @ArchTest
    static final ArchRule 分层依赖单向 = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Controller").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Mapper").definedBy("..mapper..")
            .layer("Entity").definedBy("..entity..")
            .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Service")
            .whereLayer("Mapper").mayOnlyBeAccessedByLayers("Service", "Mapper");

    /** 摄取层不得依赖控制层：摄取链路不感知 HTTP 层。 */
    @ArchTest
    static final ArchRule 摄取层不得依赖控制层 =
            noClasses().that().resideInAPackage("com.mqtt.cloud.ingest")
                    .should().dependOnClassesThat().resideInAPackage("..controller..");

    /**
     * MQTT 回调线程所在包不得直接访问数据库。
     * <p>
     * 这是 P0-1 的结构性防线：回调线程必须只做解析与投递（交给 ingest 异步落库），
     * 一旦允许在回调里直接调 Mapper，同步落库就会以"改一行"的方式回退。
     * <p>
     * 注意这里必须写<b>完整包名</b>而非 {@code "..mqtt.."}：基础包 {@code com.mqtt} 本身就含
     * {@code mqtt} 段，{@code "..mqtt.."} 会连带匹配 {@code com.mqtt.cloud.mapper} 等所有子包，
     * 产生大量误报（实测 89 条）。
     */
    @ArchTest
    static final ArchRule 回调线程所在包不得直接依赖Mapper =
            noClasses().that().resideInAPackage("com.mqtt.cloud.mqtt")
                    .should().dependOnClassesThat().resideInAPackage("..mapper..");

    /**
     * Service 接口包不得直接依赖 Mapper：持久化细节只能出现在 {@code service.impl}。
     * <p>
     * 防止 {@code getBaseMapper()} 之类的"借道实现"泄漏到接口包 —— 这正是
     * {@code DeviceMonitorService} 越界的成因，修正后由本规则守住不再回退。
     */
    @ArchTest
    static final ArchRule 服务接口包不得直接依赖Mapper =
            noClasses().that().resideInAPackage("com.mqtt.cloud.service")
                    .should().dependOnClassesThat().resideInAPackage("..mapper..");
}