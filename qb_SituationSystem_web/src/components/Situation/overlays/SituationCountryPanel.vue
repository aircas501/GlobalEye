<template>
  <div v-if="visible" class="country-panel">
    <div class="country-panel-title">国家(Country)</div>
    <div class="country-selected-row">
      <img
        v-if="selectedFlagUrl"
        :src="selectedFlagUrl"
        class="country-flag-img"
        alt=""
        loading="lazy"
        @error="$emit('flag-error')"
      />
      <span v-else class="country-flag-placeholder" aria-hidden="true" />
      <span class="country-selected-text">{{ selectedCountryLineText }}</span>
    </div>

    <input
      :value="countrySearch"
      type="search"
      class="country-search"
      placeholder="请输入国家名称"
      autocomplete="off"
      spellcheck="false"
      @input="$emit('search-input', $event)"
      @keydown.enter.prevent="$emit('apply-first-match')"
    />

    <ul v-if="countrySearch && filteredCountries.length" class="country-suggest">
      <li
        v-for="c in filteredCountries"
        :key="c.id"
        @mousedown.prevent="$emit('select-country', c.id)"
      >
        <span class="country-zh">{{ c.nameZh }}</span>
        <span class="country-en">({{ c.nameEn }})</span>
      </li>
    </ul>

    <p v-else-if="countrySearch && !filteredCountries.length" class="country-empty">
      无匹配项
    </p>
  </div>
</template>

<script>
/**
 * 国家检索与当前选中展示（GeoJSON 高亮逻辑仍在父组件）
 * @module components/Situation/overlays/SituationCountryPanel
 */
export default {
  name: "SituationCountryPanel",
  props: {
    visible: { type: Boolean, default: true },
    selectedFlagUrl: { type: String, default: "" },
    selectedCountryLineText: { type: String, default: "" },
    countrySearch: { type: String, default: "" },
    /** 父组件计算后的候选列表（已截断条数） */
    filteredCountries: { type: Array, default: () => [] }
  }
};
</script>
