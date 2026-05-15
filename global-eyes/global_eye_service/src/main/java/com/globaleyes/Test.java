package com.globaleyes;

import cn.hutool.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
public class Test {

    @GetMapping("/info-card")
    public Object get2() {
        List<InfoItem> itemList = List.of(
                new InfoItem("伊朗铀浓缩", "60% 丰度"),
                new InfoItem("美军部署", "中东增派航母战斗群"),
                new InfoItem("原油价格", "布伦特突破 $95/桶")
        );
        log.info("info-card:{}", itemList);
        return new SituationInfo("美伊局势情报", "🌍", itemList);
    }

    @GetMapping("/data-table")
    public Object get() {
        // 1. 构造表格标题 + 列
        String tableTitle = "地区军事部署";
        List<String> columns = List.of("国家", "兵力", "装备", "态势");

        // 2. 构造数据行
        List<Map<String, String>> dataList = new ArrayList<>();

        Map<String, String> usa = new HashMap<>();
        usa.put("国家", "美国");
        usa.put("兵力", "5.5万");
        usa.put("装备", "航母×2");
        usa.put("态势", "威慑");

        Map<String, String> iran = new HashMap<>();
        iran.put("国家", "伊朗");
        iran.put("兵力", "12万");
        iran.put("装备", "导弹×3000+");
        iran.put("态势", "防御");

        Map<String, String> israel = new HashMap<>();
        israel.put("国家", "以色列");
        israel.put("兵力", "3万");
        israel.put("装备", "F-35×12");
        israel.put("态势", "警戒");

        dataList.add(usa);
        dataList.add(iran);
        dataList.add(israel);
        log.info("data-table:{}", dataList);
        // 3. 封装返回对象
        return new TableData(tableTitle, columns, dataList);
    }

    public static class SituationInfo {
        private String title;
        private String icon;
        private List<InfoItem> items;

        public SituationInfo() {}

        public SituationInfo(String title, String icon, List<InfoItem> items) {
            this.title = title;
            this.icon = icon;
            this.items = items;
        }

        // getter setter
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public List<InfoItem> getItems() {
            return items;
        }

        public void setItems(List<InfoItem> items) {
            this.items = items;
        }
    }

    public static class InfoItem {
        private String label;
        private String value;

        public InfoItem() {}

        public InfoItem(String label, String value) {
            this.label = label;
            this.value = value;
        }

        // getter setter
        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public  static class TableData {
        private String tableTitle;       // 表格标题
        private List<String> columns;    // 列名
        private List<Map<String, String>> data; // 表格数据

        // 无参构造
        public TableData() {}

        // 全参构造
        public TableData(String tableTitle, List<String> columns, List<Map<String, String>> data) {
            this.tableTitle = tableTitle;
            this.columns = columns;
            this.data = data;
        }

        // getter & setter
        public String getTableTitle() {
            return tableTitle;
        }

        public void setTableTitle(String tableTitle) {
            this.tableTitle = tableTitle;
        }

        public List<String> getColumns() {
            return columns;
        }

        public void setColumns(List<String> columns) {
            this.columns = columns;
        }

        public List<Map<String, String>> getData() {
            return data;
        }

        public void setData(List<Map<String, String>> data) {
            this.data = data;
        }
    }
}
