import Vue from "vue";
import App from "./App.vue";
import "./styles/theme.css";

Vue.config.productionTip = false;
window.CESIUM_BASE_URL = "/cesium";

new Vue({
  render: (h) => h(App)
}).$mount("#app");
