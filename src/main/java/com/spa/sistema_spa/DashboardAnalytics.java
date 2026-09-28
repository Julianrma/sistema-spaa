package com.spa.sistema_spa;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

public class DashboardAnalytics {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private final List<BranchStat> branchStats;
    private final List<TimeBandStat> timeBandStats;
    private final String leaderName;
    private final int leaderCount;
    private final String peakName;
    private final int peakCount;

    private DashboardAnalytics(List<BranchStat> branchStats, List<TimeBandStat> timeBandStats,
                               String leaderName, int leaderCount, String peakName, int peakCount) {
        this.branchStats = branchStats;
        this.timeBandStats = timeBandStats;
        this.leaderName = leaderName;
        this.leaderCount = leaderCount;
        this.peakName = peakName;
        this.peakCount = peakCount;
    }

    public static DashboardAnalytics from(List<Reservation> reservations, List<Branch> branchList) {
        Map<Integer, Integer> byBranch = new LinkedHashMap<>();
        Map<Integer, String> branchNames = new LinkedHashMap<>();
        branchList.stream().forEach(branch -> {
            byBranch.put(branch.getId().intValue(), 0);
            branchNames.put(branch.getId().intValue(), branch.getName());
        });
        Map<String, Integer> byBand = new LinkedHashMap<>();
        byBand.put("Mañana (08:00 - 12:00)", 0);
        byBand.put("Mediodía (12:00 - 15:00)", 0);
        byBand.put("Tarde (15:00 - 20:00)", 0);
        byBand.put("Noche (20:00 en adelante)", 0);

        for (Reservation reservation : reservations) {
            byBranch.computeIfPresent(reservation.getBranchId(), (key, value) -> value + 1);
            String band = bandFor(reservation.getReservationTime());
            byBand.computeIfPresent(band, (key, value) -> value + 1);
        }

        int total = reservations.size();
        List<BranchStat> branches = byBranch.entrySet().stream()
            .map(entry -> new BranchStat(branchNames.get(entry.getKey()), entry.getValue(), percentage(entry.getValue(), total)))
                .toList();
        List<TimeBandStat> bands = byBand.entrySet().stream()
                .map(entry -> new TimeBandStat(entry.getKey(), entry.getValue(), percentage(entry.getValue(), total)))
                .toList();
        BranchStat leader = branches.stream().filter(stat -> stat.count() > 0)
                .max(Comparator.comparingInt(stat -> stat.count())).orElse(null);
        TimeBandStat peak = bands.stream().filter(stat -> stat.count() > 0)
                .max(Comparator.comparingInt(stat -> stat.count())).orElse(null);
        return new DashboardAnalytics(branches, bands,
                leader == null ? "Sin datos" : leader.name(), leader == null ? 0 : leader.count(),
                peak == null ? "Sin datos" : peak.name(), peak == null ? 0 : peak.count());
    }

    private static int percentage(int count, int total) {
        return total == 0 ? 0 : Math.round((count * 100f) / total);
    }

    private static String bandFor(String value) {
        try {
            LocalTime time = LocalTime.parse(value.toUpperCase(), TIME_FORMAT);
            if (time.isBefore(LocalTime.NOON)) return "Mañana (08:00 - 12:00)";
            if (time.isBefore(LocalTime.of(15, 0))) return "Mediodía (12:00 - 15:00)";
            if (time.isBefore(LocalTime.of(20, 0))) return "Tarde (15:00 - 20:00)";
        } catch (DateTimeParseException ignored) {
        }
        return "Noche (20:00 en adelante)";
    }

    public List<BranchStat> getBranchStats() { return branchStats; }
    public List<TimeBandStat> getTimeBandStats() { return timeBandStats; }
    public String getLeaderName() { return leaderName; }
    public int getLeaderCount() { return leaderCount; }
    public String getPeakName() { return peakName; }
    public int getPeakCount() { return peakCount; }
}