<template>
  <div class="landing-page">
    <LandingNav />
    <LandingHero />
    <LandingTrust />
    <LandingFeatures />
    <LandingDevices />
    <LandingStats />
    <LandingScenarios />
    <LandingApi />
    <LandingCta />
    <LandingFooter />
  </div>
</template>

<script setup>
import LandingNav from '@/components/landing/LandingNav.vue'
import LandingHero from '@/components/landing/LandingHero.vue'
import LandingTrust from '@/components/landing/LandingTrust.vue'
import LandingFeatures from '@/components/landing/LandingFeatures.vue'
import LandingDevices from '@/components/landing/LandingDevices.vue'
import LandingStats from '@/components/landing/LandingStats.vue'
import LandingScenarios from '@/components/landing/LandingScenarios.vue'
import LandingApi from '@/components/landing/LandingApi.vue'
import LandingCta from '@/components/landing/LandingCta.vue'
import LandingFooter from '@/components/landing/LandingFooter.vue'
</script>

<style scoped>
.landing-page {
  min-height: 100vh;
}
</style>
