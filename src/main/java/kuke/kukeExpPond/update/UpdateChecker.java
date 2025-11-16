package kuke.kukeExpPond.update;

import kuke.kukeExpPond.KukeExpPond;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub版本更新检测器
 */
public class UpdateChecker {
    private final KukeExpPond plugin;
    private final String currentVersion;
    private final String githubApiUrl;
    private String latestVersion = null;
    private boolean hasUpdate = false;
    private boolean checkInProgress = false;
    
    // GitHub API URL for releases
    private static final String GITHUB_API_BASE = "https://api.github.com/repos/kukemc/KukeExpPond/releases/latest";
    
    public UpdateChecker(KukeExpPond plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
        // 从配置文件读取GitHub仓库信息
        String configRepo = plugin.getConfig().getString("general.update_checker.github_repo", "kukemc/KukeExpPond");
        this.githubApiUrl = "https://api.github.com/repos/" + configRepo + "/releases/latest";
    }
    
    /**
     * 异步检查更新
     */
    public CompletableFuture<Boolean> checkForUpdates() {
        // 检查是否启用更新检测
        if (!plugin.getConfig().getBoolean("general.update_checker.enable", true)) {
            return CompletableFuture.completedFuture(false);
        }
        
        if (checkInProgress) {
            return CompletableFuture.completedFuture(hasUpdate);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            checkInProgress = true;
            try {
                String latest = fetchLatestVersion();
                if (latest != null) {
                    latestVersion = latest;
                    hasUpdate = isNewerVersion(latest, currentVersion);
                    
                    // 在主线程中记录日志
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (hasUpdate) {
                                plugin.getLogger().info("发现新版本: " + latestVersion + " (当前版本: " + currentVersion + ")");
                                plugin.getLogger().info("下载地址: https://github.com/kukemc/KukeExpPond/releases/latest");
                            } else {
                                plugin.getLogger().info("当前版本已是最新版本: " + currentVersion);
                            }
                        }
                    }.runTask(plugin);
                }
            } catch (Exception e) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        plugin.getLogger().warning("检查更新失败: " + e.getMessage());
                        if (plugin.getConfig().getBoolean("general.debug", false)) {
                            e.printStackTrace();
                        }
                    }
                }.runTask(plugin);
            } finally {
                checkInProgress = false;
            }
            return hasUpdate;
        });
    }
    
    /**
     * 从GitHub API获取最新版本
     */
    private String fetchLatestVersion() throws IOException {
        URL url = new URL(githubApiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        // 设置请求头
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "KukeExpPond-UpdateChecker");
        
        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("GitHub API返回错误代码: " + responseCode);
        }
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        
        // 解析JSON响应获取tag_name
        return parseVersionFromJson(response.toString());
    }
    
    /**
     * 从JSON响应中解析版本号
     */
    private String parseVersionFromJson(String json) {
        // 简单的JSON解析，查找tag_name字段
        Pattern pattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        
        if (matcher.find()) {
            String tagName = matcher.group(1);
            // 移除v前缀（如果存在）
            if (tagName.startsWith("v")) {
                return tagName.substring(1);
            }
            return tagName;
        }
        
        return null;
    }
    
    /**
     * 比较版本号，判断是否有新版本
     */
    private boolean isNewerVersion(String latest, String current) {
        try {
            // 简单的版本比较，支持x.y.z格式
            String[] latestParts = latest.split("\\.");
            String[] currentParts = current.split("\\.");
            
            int maxLength = Math.max(latestParts.length, currentParts.length);
            
            for (int i = 0; i < maxLength; i++) {
                int latestPart = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;
                int currentPart = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
                
                if (latestPart > currentPart) {
                    return true;
                } else if (latestPart < currentPart) {
                    return false;
                }
            }
            
            return false; // 版本相同
        } catch (Exception e) {
            plugin.getLogger().warning("版本比较失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 解析版本号部分，处理非数字字符
     */
    private int parseVersionPart(String part) {
        // 移除非数字字符（如SNAPSHOT、BETA等）
        String numericPart = part.replaceAll("[^0-9]", "");
        return numericPart.isEmpty() ? 0 : Integer.parseInt(numericPart);
    }
    
    /**
     * 向管理员发送更新通知
     */
    public void notifyAdmins() {
        if (!hasUpdate || latestVersion == null) {
            return;
        }
        
        String message = plugin.getConfig().getString("update.admin_notification_message", 
            "&e[KukeExpPond] &a发现新版本 &f{latest} &7(当前: &f{current}&7)");
        String downloadMessage = plugin.getConfig().getString("update.download_message",
            "&e[KukeExpPond] &b下载地址: &fhttps://github.com/kukemc/KukeExpPond/releases/latest");
        
        message = message.replace("{latest}", latestVersion).replace("{current}", currentVersion);
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("kukeexppond.admin") || player.isOp()) {
                player.sendMessage(message.replace("&", "§"));
                player.sendMessage(downloadMessage.replace("&", "§"));
            }
        }
    }
    
    /**
     * 向指定玩家发送更新通知
     */
    public void notifyPlayer(Player player) {
        if (!hasUpdate || latestVersion == null) {
            return;
        }
        
        if (player.hasPermission("kukeexppond.admin") || player.isOp()) {
            String message = plugin.getConfig().getString("update.admin_notification_message", 
                "&e[KukeExpPond] &a发现新版本 &f{latest} &7(当前: &f{current}&7)");
            String downloadMessage = plugin.getConfig().getString("update.download_message",
                "&e[KukeExpPond] &b下载地址: &fhttps://github.com/kukemc/KukeExpPond/releases/latest");
            
            message = message.replace("{latest}", latestVersion).replace("{current}", currentVersion);
            
            player.sendMessage(message.replace("&", "§"));
            player.sendMessage(downloadMessage.replace("&", "§"));
        }
    }
    
    // Getters
    public String getCurrentVersion() {
        return currentVersion;
    }
    
    public String getLatestVersion() {
        return latestVersion;
    }
    
    public boolean hasUpdate() {
        return hasUpdate;
    }
    
    public boolean isCheckInProgress() {
        return checkInProgress;
    }
}