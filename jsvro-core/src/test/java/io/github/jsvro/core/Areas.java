package io.github.jsvro.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.IntStream;

final class Areas {
    private static final List<String> NAMES = List.of(
            "Oslo", "Bergen", "Trondheim", "Stavanger", "Tromsø", "Bodø", "Ålesund", "Drammen",
            "Kristiansand", "Fredrikstad", "Hamar", "Lillehammer", "Molde", "Harstad", "Alta", "Narvik");

    public enum AreaType { COUNTRY, COUNTY, MUNICIPALITY, POST }

    public static class Point {
        private double lat;
        private double lon;

        public Point() {
        }

        public Point(double lat, double lon) {
            this.lat = lat;
            this.lon = lon;
        }

        public double getLat() { return lat; }
        public void setLat(double lat) { this.lat = lat; }
        public double getLon() { return lon; }
        public void setLon(double lon) { this.lon = lon; }

        @Override
        public boolean equals(Object other) {
            return other instanceof Point point && lat == point.lat && lon == point.lon;
        }

        @Override
        public int hashCode() {
            return Objects.hash(lat, lon);
        }
    }

    public static class Area {
        private AreaType areaType;
        private String code;
        private String shortName;
        private String name;
        private List<Point> boundary;

        public AreaType getAreaType() { return areaType; }
        public void setAreaType(AreaType areaType) { this.areaType = areaType; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getShortName() { return shortName; }
        public void setShortName(String shortName) { this.shortName = shortName; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<Point> getBoundary() { return boundary; }
        public void setBoundary(List<Point> boundary) { this.boundary = boundary; }

        @Override
        public boolean equals(Object other) {
            return other instanceof Area area
                    && areaType == area.areaType
                    && Objects.equals(code, area.code)
                    && Objects.equals(shortName, area.shortName)
                    && Objects.equals(name, area.name)
                    && Objects.equals(boundary, area.boundary);
        }

        @Override
        public int hashCode() {
            return Objects.hash(areaType, code, shortName, name, boundary);
        }
    }

    private Areas() {
    }

    static List<Area> generate(int count) {
        Random random = new Random(42);
        return IntStream.range(0, count).mapToObj(i -> area(random, i)).toList();
    }

    static Area postal(Random random, String city) {
        String postalCode = String.format("%04d", random.nextInt(10_000));
        Area area = new Area();
        area.setAreaType(AreaType.POST);
        area.setCode(postalCode);
        area.setShortName(postalCode);
        area.setName(postalCode + " " + city);
        area.setBoundary(polygon(random));
        return area;
    }

    private static Area area(Random random, int index) {
        AreaType type = switch (random.nextInt(20)) {
            case 0 -> AreaType.COUNTRY;
            case 1, 2, 3 -> AreaType.COUNTY;
            default -> AreaType.MUNICIPALITY;
        };
        String name = NAMES.get(random.nextInt(NAMES.size()));

        Area area = new Area();
        area.setAreaType(type);
        area.setCode(String.format("NO-%07d", index));
        area.setShortName(name.substring(0, 3).toUpperCase());
        area.setName(name + " " + type.name().toLowerCase());
        area.setBoundary(polygon(random));
        return area;
    }

    private static List<Point> polygon(Random random) {
        double centreLat = 58 + random.nextDouble() * 13;
        double centreLon = 5 + random.nextDouble() * 25;
        double radius = 0.05 + random.nextDouble() * 0.5;
        int corners = 4 + random.nextInt(13);

        List<Point> boundary = new ArrayList<>(corners);
        for (int i = 0; i < corners; i++) {
            double angle = 2 * Math.PI * i / corners;
            double distance = radius * (0.7 + random.nextDouble() * 0.3);
            boundary.add(new Point(
                    round(centreLat + distance * Math.sin(angle)),
                    round(centreLon + distance * Math.cos(angle))));
        }
        return List.copyOf(boundary);
    }

    private static double round(double coordinate) {
        return Math.round(coordinate * 1_000_000d) / 1_000_000d;
    }
}
