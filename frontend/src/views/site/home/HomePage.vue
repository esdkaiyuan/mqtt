<template>
  <div class="home-page">
    <SiteNav />
    <SiteHero />
    <SiteTrust />
    <SiteFeatures />
    <SiteDevices />
    <SiteStats />
    <SiteScenarios />
    <SiteApi />
    <SiteCta />
    <SiteFooter />
  </div>
</template>

<script setup>
import SiteNav from '@/components/site/SiteNav.vue'
import SiteHero from '@/components/site/SiteHero.vue'
import SiteTrust from '@/components/site/SiteTrust.vue'
import SiteFeatures from '@/components/site/SiteFeatures.vue'
import SiteDevices from '@/components/site/SiteDevices.vue'
import SiteStats from '@/components/site/SiteStats.vue'
import SiteScenarios from '@/components/site/SiteScenarios.vue'
import SiteApi from '@/components/site/SiteApi.vue'
import SiteCta from '@/components/site/SiteCta.vue'
import SiteFooter from '@/components/site/SiteFooter.vue'
</script>

<style scoped>
.home-page {
  min-height: 100vh;
}
</style>