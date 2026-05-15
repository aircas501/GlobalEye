const { defineConfig } = require("@vue/cli-service");
const CopyWebpackPlugin = require("copy-webpack-plugin");
const webpack = require("webpack");
const path = require("path");

module.exports = defineConfig({
  /** 允许在浏览器内编译用户粘贴的模板（情报组件生成面板预览） */
  runtimeCompiler: true,
  transpileDependencies: true,
  publicPath: process.env.VUE_APP_PUBLIC_PATH || "/",
  devServer: {
    port: 8080,
    client: {
      overlay: {
        /** Chrome 在缩放/布局链中偶发抛出，多为无害；避免 dev overlay 误挡屏 */
        runtimeErrors: (error) => {
          const msg = error && error.message ? String(error.message) : String(error || "");
          if (/ResizeObserver loop/i.test(msg)) return false;
          return true;
        }
      }
    },
    /**
     * 默认 true 会对响应 gzip；SSE（text/event-stream）会被缓冲，EventSource 长时间无数据或挂死。
     * 开发环境关闭压缩，普通 JSON 与流式接口才能稳定经代理工作。
     */
    compress: false,
    proxy: {
      "/vessel-api": {
        target: "localhost:XXXX",
        changeOrigin: true,
        pathRewrite: {
          "^/vessel-api": "/api"
        }
      },
      "/sat-api": {
        target: "localhost:XXXX",
        changeOrigin: true,
        pathRewrite: {
          "^/sat-api": "/api"
        }
      },
      "/hot-api": {
        target: "localhost:XXXX",
        changeOrigin: true,
        pathRewrite: {
          "^/hot-api": "/api"
        }
      },
      "/overpass-api": {
        target: "localhost:XXXX",
        changeOrigin: true,
        pathRewrite: {
          "^/overpass-api": "/api"
        }
      },
      "/recommendation-api": {
        target: "localhost:XXXX",
        changeOrigin: true,
        pathRewrite: {
          "^/recommendation-api": "/api"
        }
      },
      /**
       * aicode / 讨论室 / 插件 共用：浏览器请求 `/intelligentization-api/api/...`，
       * 去掉前缀后转发到 XXXX 根路径（后端实际为 `/api/...`）。
       * 生产环境请在 Nginx 等对 `/intelligentization-api` 做同等反代与 strip（与此前缀一致）。
       */
      "/intelligentization-api": {
        target: "localhost:XXXX",
        changeOrigin: true,
        pathRewrite: {
          "^/intelligentization-api": ""
        },
        /** 长连接 SSE：给足代理等待时间（毫秒），避免中间层过早关连接 */
        timeout: 3600000,
        proxyTimeout: 3600000,
        onProxyRes(proxyRes) {
          const ct = String(proxyRes.headers["content-type"] || "").toLowerCase();
          if (ct.includes("text/event-stream")) {
            delete proxyRes.headers["content-encoding"];
          }
        }
      },
      "/graph-api": {
        target: "localhost:XXXX",
        changeOrigin: true,
        pathRewrite: {
          "^/graph-api": ""
        }
      },
      "/satellite-ws": {
        target: "localhost:XXXX",
        changeOrigin: true,
        ws: true,
        pathRewrite: {
          "^/satellite-ws": ""
        }
      },
      
    }
  },
  configureWebpack: {
    resolve: {
      alias: {
        /**
         * Vue 2.7 的 exports 默认解析为 runtime 包，动态 template（AI 面板预览）需要完整版。
         * 仅 runtimeCompiler: true 在部分链路下仍会得到 vue.runtime.esm.js，故显式别名。
         */
        vue$: path.resolve(__dirname, "node_modules/vue/dist/vue.esm.js"),
        // Use bundled Cesium build to avoid ESM/runtime incompatibility in Vue2 pipeline.
        cesium$: path.resolve(
          __dirname,
          "node_modules/cesium/Build/Cesium/Cesium.js"
        ),
        // satellite.js v4 主入口为 lib/index.js；v7 为 dist/index.js，误解析会 ENOENT
        "satellite.js": path.resolve(
          __dirname,
          "node_modules/satellite.js/lib/index.js"
        )
      },
      fallback: {
        url: require.resolve("url/"),
        http: require.resolve("stream-http"),
        https: require.resolve("https-browserify"),
        zlib: require.resolve("browserify-zlib"),
        buffer: require.resolve("buffer/"),
        stream: require.resolve("stream-browserify"),
        assert: require.resolve("assert/")
      }
    },
    plugins: [
      new webpack.ProvidePlugin({
        process: "process/browser",
        Buffer: ["buffer", "Buffer"]
      }),
      new CopyWebpackPlugin({
        patterns: [
          {
            from: path.join("node_modules", "cesium", "Build", "Cesium"),
            to: "cesium"
          }
        ]
      })
    ]
  }
});
