package com.globaleyes.crawler.util;

import com.globaleyes.crawler.pojo.vo.StarSpot;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.Constants;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Slf4j
// @Service
public class StarSpotCalcUtil {
    // 地球平均半径（km）
    private static final double EARTH_RADIUS_KM = 6371.0;

    // Orekit 框架引用
    private static Frame gcrfFrame = null;  // GCRF 惯性系

    // 地球赤道半径（米）- WGS84
    private static final double EARTH_EQUATORIAL_RADIUS_M = 6378137.0;
    static {
        File file = new File("/orekit-data");
        if (!file.exists()) {
            file = new File("D:\\workspace\\idea\\qbtsxt\\global-eyes\\global_eye_service\\src\\main\\resources\\orekit-data");
        }

        DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
        manager.addProvider(new DirectoryCrawler(file));
       try {
           // 获取天球参考系（GCRF）
           gcrfFrame = FramesFactory.getGCRF();
       } catch (Exception e) {
           throw new RuntimeException("Orekit 初始化失败：" + e.getMessage(), e);
       }
    }



    /**
     * 计算卫星在指定时刻的位置(超级准确)
     * @param tleLine1
     * @param tleLine2
     * @param epoch
     * @return
     * @throws Exception
     */
    public static StarSpot calculateSatellitePosition(LocalDateTime epoch, String tleLine1, String tleLine2)  {
        // 1. 解析 TLE 数据
        TLEData tleData = null;
        try {
            tleData = parseTLE(tleLine1, tleLine2);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // 2. 计算目标时刻的卫星位置（简化 SGP4 模型）
        double[] positionECI = propagateSGP4(tleData, epoch.atZone(ZoneId.of("UTC")));

        // 3. 坐标转换：ECI → ECEF（考虑地球自转）
        double[] positionECEF = convertECItoECEF(positionECI, epoch.atZone(ZoneId.of("UTC")));

        // 4. 计算卫星到地心的距离
        double r = Math.sqrt(
                positionECEF[0] * positionECEF[0] +
                        positionECEF[1] * positionECEF[1] +
                        positionECEF[2] * positionECEF[2]
        );

        // 5. 计算星下点位置（地心经纬度）
        double nadirLat = Math.asin(positionECEF[2] / r);
        double nadirLon = Math.atan2(positionECEF[1], positionECEF[0]);
        double latitude = Math.toDegrees(nadirLat);
        double longitude = Math.toDegrees(nadirLon);
        // 5. 计算卫星高度（假设地球为球体）
        double altitude = (r / 1000.0) - EARTH_RADIUS_KM; // 转换为 km
        return new StarSpot(latitude, longitude, altitude);
    }



    private static TLEData parseTLE(String line1, String line2) throws Exception {
        TLEData tle = new TLEData();

        // 解析 Line 1
        if (!line1.trim().startsWith("1")) {
            throw new IllegalArgumentException("TLE 第一行必须以'1'开头");
        }
        tle.noradId = line1.substring(2, 7).trim();
        String epochStr = line1.substring(18, 32).trim();
        tle.epochYear = Double.parseDouble(epochStr.substring(0, 2));
        tle.epochDay = Double.parseDouble(epochStr.substring(2));

        // 解析 Line 2
        if (!line2.trim().startsWith("2")) {
            throw new IllegalArgumentException("TLE 第二行必须以'2'开头");
        }
        tle.inclination = Double.parseDouble(line2.substring(8, 16).trim());
        tle.raan = Double.parseDouble(line2.substring(17, 25).trim());
        tle.eccentricity = Double.parseDouble("0." + line2.substring(26, 33).trim());
        tle.argOfPerigee = Double.parseDouble(line2.substring(34, 42).trim());
        tle.meanAnomaly = Double.parseDouble(line2.substring(43, 51).trim());
        // Mean Motion (rev/day) 在第 53-63 列
        tle.meanMotion = Double.parseDouble(line2.substring(52, 63).trim());

        return tle;
    }

    /**
     * 简化 SGP4 模型传播（已替换为 Orekit 实现）
     *
     * 注意：为了提升精度，此方法现在调用 Orekit 进行精确计算
     */
    private static double[] propagateSGP4(TLEData tle, ZonedDateTime dateTime) {
        // 使用 Orekit 进行精确的轨道传播
        return propagateWithOrekit(tle, dateTime);
    }


    /**
     * TLE 数据结构
     */

    @Data
    private static class TLEData {
        String noradId;           // NORAD ID
        double epochYear;         // 历元年
        double epochDay;          // 历元日
        double meanMotion;        // 平均运动（rev/day）
        double eccentricity;      // 偏心率
        double inclination;       // 轨道倾角（deg）
        double raan;              // 升交点赤经（deg）
        double argOfPerigee;      // 近地点幅角（deg）
        double meanAnomaly;       // 平近点角（deg）
    }


    /**
     * ECI 转 ECEF 坐标
     *
     * 使用简化的方式考虑地球自转（简化方法已足够精确）
     */
    private static double[] convertECItoECEF(double[] eci, ZonedDateTime dateTime) {
        // 计算格林尼治恒星时角（简化计算）
        double gast = computeGAST(dateTime);

        double x = eci[0];
        double y = eci[1];
        double z = eci[2];

        double cosTheta = Math.cos(gast);
        double sinTheta = Math.sin(gast);

        // 绕 Z 轴旋转
        double xECEF = x * cosTheta + y * sinTheta;
        double yECEF = -x * sinTheta + y * cosTheta;
        double zECEF = z;

        return new double[]{xECEF, yECEF, zECEF};
    }

    /**
     * 计算格林尼治平恒星时（GMST）
     */
    private static double computeGAST(ZonedDateTime dateTime) {
        int year = dateTime.getYear();
        int month = dateTime.getMonthValue();
        int day = dateTime.getDayOfMonth();
        double hour = dateTime.getHour() + dateTime.getMinute() / 60.0 +
                dateTime.getSecond() / 3600.0;

        if (month <= 2) {
            year -= 1;
            month += 12;
        }

        int A = year / 100;
        int B = 2 - A + A / 4;
        double JD = Math.floor(365.25 * (year + 4716)) +
                Math.floor(30.6001 * (month + 1)) +
                day + B - 1524.5 + hour / 24.0;

        double T = (JD - 2451545.0) / 36525.0;
        double gmst = 280.46061837 +
                360.98564736629 * (JD - 2451545.0) +
                0.000387933 * T * T -
                T * T * T / 38710000.0;

        gmst = gmst % 360.0;
        if (gmst < 0) gmst += 360.0;

        return Math.toRadians(gmst);
    }

    /**
     * 使用 Orekit 进行开普勒轨道传播
     *
     * 基于 TLE 提取的轨道根数，使用 Orekit 的 KeplerianPropagator 进行精确轨道传播
     */
    private static double[] propagateWithOrekit(TLEData tle, ZonedDateTime dateTime) {
        try {
            // 计算半长轴（根据平均运动）
            double n = tle.meanMotion * 2 * Math.PI / 86400.0;  // rad/s
            double a = FastMath.pow(Constants.EIGEN5C_EARTH_MU / (n * n), 1.0/3.0);  // 半长轴（米）

            // 从 TLE 数据中提取轨道根数并创建轨道
            int year = tle.epochYear >= 57 ? (int)(1900 + tle.epochYear) : (int)(2000 + tle.epochYear);
            Instant epochInstant = java.time.LocalDateTime.of(year, 1, 1, 0, 0, 0)
                    .plusSeconds((long)((tle.epochDay - 1) * 86400))
                    .atZone(java.time.ZoneOffset.UTC)
                    .toInstant();
            AbsoluteDate epochDate = new AbsoluteDate(epochInstant);

            // 目标时刻
            AbsoluteDate targetDate = new AbsoluteDate(dateTime.toInstant());

            // 创建开普勒轨道
            KeplerianOrbit orbit = new KeplerianOrbit(
                    a,                                      // 半长轴（米）
                    tle.eccentricity,                       // 偏心率
                    Math.toRadians(tle.inclination),        // 倾角（弧度）
                    Math.toRadians(tle.argOfPerigee),       // 近地点幅角（弧度）
                    Math.toRadians(tle.raan),               // 升交点赤经（弧度）
                    Math.toRadians(tle.meanAnomaly),        // 平近点角（弧度）
                    PositionAngleType.MEAN,
                    gcrfFrame,                              // GCRF 惯性系
                    epochDate,
                    Constants.EIGEN5C_EARTH_MU              // 地球引力常数
            );

            // 创建开普勒传播器
            KeplerianPropagator propagator = new KeplerianPropagator(orbit);

            // 传播到目标时刻
            SpacecraftState stateAtDate = propagator.propagate(targetDate);

            // 获取卫星在 GCRF 中的位置
            Vector3D positionGCRF = stateAtDate.getPVCoordinates().getPosition();

            return new double[]{positionGCRF.getX(), positionGCRF.getY(), positionGCRF.getZ()};

        } catch (Exception e) {
            throw new RuntimeException("Orekit 轨道传播失败：" + e.getMessage(), e);
        }
    }

}
