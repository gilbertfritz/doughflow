package com.gf.doughflow.workspace;

import com.gf.doughflow.swing.UIHandler;
import com.gf.doughflow.translator.exporter.FileCreator;
import com.gf.doughflow.translator.exporter.XhbExporter;
import com.gf.doughflow.translator.importer.FileImporter;
import com.gf.doughflow.translator.model.Account;
import com.gf.doughflow.translator.model.Transaction;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.io.FileUtils;

public class WorkSpace {

    private static final Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    private final String FILE_ACTUAL = "actual.xhb";
    private final String FOLDER_ACTUAL = "actual";
    private final String FOLDER_BACKUP = "backup";
    private final String FOLDER_IMPORT = "import";
    private final String FOLDER_TEMP = "temp";
    private final String FOLDER_PROCESSED = "processed";

    private final File actualFile;
    private final File workDir;
    private final File actualDir;
    private final File backupDir;
    private final File importDir;
    private final File tempDir;
    private final Map<Integer, Account> accounts;

    private final SimpleDateFormat sdf = new SimpleDateFormat("ddMMyyyy_HHmmssSSS");

    public WorkSpace(String wd) {
        this.accounts = AccountRegistry.getAccounts();
        this.workDir = checkExistsAndCreateDir(wd);
        this.actualDir = checkExistsAndCreateDir(workDir.getAbsolutePath() + "/" + FOLDER_ACTUAL);
        this.actualFile = checkExistsAndCreateActualFile(actualDir.getAbsolutePath() + "/" + FILE_ACTUAL);
        this.backupDir = checkExistsAndCreateDir(workDir.getAbsolutePath() + "/" + FOLDER_BACKUP);
        this.importDir = checkExistsAndCreateDir(workDir.getAbsolutePath() + "/" + FOLDER_IMPORT);
        for (Integer accountId : accounts.keySet()) {
            String importDirAccount = this.importDir + "/" + accountNameToFileName(accounts.get(accountId).getName());
            String importDirAccountProcessed = importDirAccount + "/" + FOLDER_PROCESSED;
            accounts.get(accountId).setImportDir(importDirAccount);
            checkExistsAndCreateDir(importDirAccount);
            accounts.get(accountId).setImportDirProcessed(importDirAccountProcessed);
            checkExistsAndCreateDir(importDirAccountProcessed);
        }
        this.tempDir = checkExistsAndCreateDir(workDir.getAbsolutePath() + "/" + FOLDER_TEMP);
        logger.info("Working on workspace at " + wd);
    }

    public void importData(UIHandler wdc) {
        for (Account account : accounts.values()) {
            long lastmod = 0;
            File latestFile = null;
            File[] files = new File(account.getImportDir()).listFiles();
            for (File f : files) {
                if (f.isDirectory()) {
                    continue;
                }
                if (f.lastModified() > lastmod) {
                    lastmod = f.lastModified();
                    latestFile = f;
                }
            }
            if (latestFile != null) {
                logger.fine("Importing file '" + latestFile.getAbsolutePath() + "' for account '" + account.getName() + "'");
                createBackup("beforeImport");
                List<Transaction> freshTransactions = FileImporter.importRecordPerLine(account.getImporter(), latestFile);
                int imported = FileImporter.mergeIntoXhb(this.actualFile, freshTransactions);
                String msg = "Imported " + imported + " transactions of account '" + account.getName() + "'";
                if (imported > 0) {
                    logger.info(msg);
                    wdc.showImportedMessage(msg);
                }
            }
            for (File f : files) {
                if (f.isFile()) {
                    try {
                        FileUtils.moveFile(f, new File(account.getImportDirProcessed() + "/" + f.getName()));
                        logger.fine("Moved file '" + f.getAbsolutePath() + "' into '" + account.getImportDirProcessed() + "'");
                    } catch (IOException ex) {
                        logger.warning("Could not move file '" + f.getAbsolutePath() + "' into '" + account.getImportDirProcessed() + "'");
                    }
                }
            }
        }
    }

    public void createBackup(String comment) {
        File destFile = new File(getBackupDir() + "/backup.xhb_" + sdf.format(new Date()) + ((comment != null) ? "_" + comment : ""));
        try {
            FileUtils.copyFile(getActualFile(), destFile);
        } catch (IOException ex) {
            Logger.getLogger(WorkSpace.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private String accountNameToFileName(String accountName) {
        return accountName.replaceAll(" ", "_");
    }

    private File checkExistsAndCreateActualFile(String filePath) {
        File actual = new File(filePath);
        if (!actual.exists()) {
            FileCreator fc = new FileCreator(createInitializingExporter(), new ArrayList());
            fc.exportFile(actual);
            System.out.println("created initial xhb file: '" + filePath + "'.");
        } else if (!actual.isFile()) {
            throw new RuntimeException("File '" + actual.getAbsolutePath() + "' is not a file!");
        }
        return actual;
    }

    private XhbExporter createInitializingExporter() {
        XhbExporter xhbExporter = new XhbExporter();
        xhbExporter.setAccounts(accounts.values());
        xhbExporter.setCategories(new ArrayList());
        return xhbExporter;
    }

    private File checkExistsAndCreateDir(String dirName) {
        File dir = new File(dirName);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new RuntimeException("could not create directory: '" + dir.getAbsolutePath() + "'.");
            }
            System.out.println("created directory: '" + dirName + "'.");
        } else if (!dir.isDirectory()) {
            throw new RuntimeException(dir.getAbsolutePath() + " is not a directory.");
        }
        return dir;
    }

    public File getActualFile() {
        return actualFile;
    }

    public File getWorkDir() {
        return workDir;
    }

    public File getActualDir() {
        return actualDir;
    }

    public File getBackupDir() {
        return backupDir;
    }

    public File getImportDir() {
        return importDir;
    }

    public File getTempDir() {
        return tempDir;
    }
}
