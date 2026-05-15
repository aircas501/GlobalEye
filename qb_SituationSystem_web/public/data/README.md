# 国家边界数据 `countries.geojson`

用于 Cesium 在“球上点选国家 + 搜索定位国家”。

路径约定：

`public/data/countries.geojson`

文件要求（最少）：
- GeoJSON FeatureCollection
- 每个 Feature 里包含国家名称字段（常见字段名：`name` / `NAME` / `admin`）

加载后系统会自动：
- 构建可搜索国家列表
- 在鼠标悬停/点击时高亮并写入 Vuex：`geo.selectedCountry`
