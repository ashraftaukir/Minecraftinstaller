package com.acimis.minecraftinstaller;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.regex.Pattern;

public class FileUtils {

    public static boolean isValidMinecraftFile(String fileName) {
        if (fileName == null) return false;
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(Constants.MCWORLD_EXTENSION) ||
                lowerName.endsWith(Constants.MCPACK_EXTENSION);
    }

    public static String getFileExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf('.');
        return lastDot >= 0 ? fileName.substring(lastDot) : "";
    }

    public static String sanitizeFileName(String fileName) {
        if (fileName == null) return "unnamed";

        // Remove extension for folder name
        String nameWithoutExt = fileName;
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            nameWithoutExt = fileName.substring(0, lastDot);
        }

        // Replace invalid characters with underscores
        return nameWithoutExt.replaceAll("[^a-zA-Z0-9\\-_\\.]", "_");
    }

    public static MinecraftFileType detectFileType(Context context, Uri fileUri) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
             ZipInputStream zipStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            boolean hasLevelDat = false;
            boolean hasBehaviorManifest = false;
            boolean hasResourceManifest = false;

            while ((entry = zipStream.getNextEntry()) != null) {
                String entryName = entry.getName().toLowerCase();

                if (entryName.equals("level.dat")) {
                    hasLevelDat = true;
                } else if (entryName.equals("manifest.json")) {
                    // Read manifest to determine type
                    String manifestContent = readZipEntryContent(zipStream);
                    try {
                        JSONObject manifest = new JSONObject(manifestContent);
                        JSONObject header = manifest.optJSONObject("header");
                        if (header != null) {
                            JSONArray modules = manifest.optJSONArray("modules");
                            if (modules != null && modules.length() > 0) {
                                JSONObject module = modules.getJSONObject(0);
                                String type = module.optString("type", "");

                                if ("resources".equals(type)) {
                                    hasResourceManifest = true;
                                } else if ("data".equals(type)) {
                                    hasBehaviorManifest = true;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // If we can't parse manifest, continue checking other indicators
                    }
                }
            }

            // Determine file type based on contents
            if (hasLevelDat) {
                return MinecraftFileType.WORLD;
            } else if (hasResourceManifest) {
                return MinecraftFileType.RESOURCE_PACK;
            } else if (hasBehaviorManifest) {
                return MinecraftFileType.BEHAVIOR_PACK;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return MinecraftFileType.UNKNOWN;
    }

    private static String readZipEntryContent(ZipInputStream zipStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int count;
        while ((count = zipStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, count);
        }
        return buffer.toString("UTF-8");
    }

    public static boolean extractZipToFolder(Context context, Uri zipUri, DocumentFile targetFolder) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(zipUri);
             ZipInputStream zipStream = new ZipInputStream(inputStream)) {

            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    // Create directory
                    createDirectoryPath(targetFolder, entry.getName());
                } else {
                    // Create file
                    DocumentFile file = createFilePath(targetFolder, entry.getName());
                    if (file != null) {
                        try (OutputStream outputStream = context.getContentResolver().openOutputStream(file.getUri())) {
                            copyStream(zipStream, outputStream);
                        }
                    }
                }
            }
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static DocumentFile createDirectoryPath(DocumentFile baseFolder, String relativePath) {
        String[] pathParts = relativePath.split("/");
        DocumentFile currentFolder = baseFolder;

        for (String part : pathParts) {
            if (!part.isEmpty()) {
                DocumentFile existingFolder = null;
                for (DocumentFile file : currentFolder.listFiles()) {
                    if (file.isDirectory() && part.equals(file.getName())) {
                        existingFolder = file;
                        break;
                    }
                }

                if (existingFolder != null) {
                    currentFolder = existingFolder;
                } else {
                    currentFolder = currentFolder.createDirectory(part);
                    if (currentFolder == null) return null;
                }
            }
        }

        return currentFolder;
    }

    private static DocumentFile createFilePath(DocumentFile baseFolder, String relativePath) {
        String[] pathParts = relativePath.split("/");
        DocumentFile currentFolder = baseFolder;

        // Navigate/create directories
        for (int i = 0; i < pathParts.length - 1; i++) {
            String part = pathParts[i];
            if (!part.isEmpty()) {
                DocumentFile existingFolder = null;
                for (DocumentFile file : currentFolder.listFiles()) {
                    if (file.isDirectory() && part.equals(file.getName())) {
                        existingFolder = file;
                        break;
                    }
                }

                if (existingFolder != null) {
                    currentFolder = existingFolder;
                } else {
                    currentFolder = currentFolder.createDirectory(part);
                    if (currentFolder == null) return null;
                }
            }
        }

        // Create file
        String fileName = pathParts[pathParts.length - 1];
        return currentFolder.createFile("application/octet-stream", fileName);
    }

    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    public enum MinecraftFileType {
        WORLD,
        RESOURCE_PACK,
        BEHAVIOR_PACK,
        UNKNOWN
    }
}