package ch.wetwer.moviedbapi.controller;

import ch.wetwer.moviedbapi.data.uploadFile.UploadFile;
import ch.wetwer.moviedbapi.data.uploadFile.UploadFileDao;
import ch.wetwer.moviedbapi.data.uploadFile.VideoType;
import ch.wetwer.moviedbapi.data.user.UserDao;
import ch.wetwer.moviedbapi.service.FileNameService;
import ch.wetwer.moviedbapi.service.FileSizeService;
import ch.wetwer.moviedbapi.service.auth.UserAuthService;
import ch.wetwer.moviedbapi.service.filehandler.SettingsService;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

/**
 * @author Wetwer
 * @project movie-score
 * @package ch.wetwer.moviedbapi.controller
 * @created 27.12.2018
 **/

@Controller
@RequestMapping("upload")
public class UploadController {

    private UploadFileDao uploadFileDao;
    private UserDao userDao;

    private SettingsService settingsService;
    private UserAuthService userAuthService;
    private FileNameService fileNameService;

    public UploadController(UploadFileDao uploadFileDao, UserDao userDao, SettingsService settingsService,
                            UserAuthService userAuthService, FileNameService fileNameService) {
        this.uploadFileDao = uploadFileDao;
        this.userDao = userDao;
        this.settingsService = settingsService;
        this.userAuthService = userAuthService;
        this.fileNameService = fileNameService;
    }


    @GetMapping
    public String getUploadPage(Model model, HttpServletRequest request) {
        if (userAuthService.isUser(model, request)) {
            userAuthService.log(this.getClass(), request);
            List<UploadFile> uploadFileList = uploadFileDao.getAll();
            model.addAttribute("files", uploadFileList);

            model.addAttribute("filesize", new FileSizeService());
            model.addAttribute("page", "upload");
            return "template";
        }
        return "redirect:/";
    }

    @GetMapping("preview/{hash}")
    public String getPreviewPage(@PathVariable("hash") int hash, Model model, HttpServletRequest request) {
        if (userAuthService.isAdministrator(model, request)) {
            userAuthService.log(this.getClass(), request);
            model.addAttribute("file", uploadFileDao.getByHash(hash));
            model.addAttribute("page", "uploadPreview");
            return "template";
        }
        return "redirect:/upload";
    }

    @PostMapping("movie")
    public String upload(@RequestParam("movie") MultipartFile multipartFile,
                         HttpServletRequest request) throws IOException {
        if (userAuthService.isUser(request)) {
            userAuthService.log(this.getClass(), request);

            InputStream fileStream;
            fileStream = multipartFile.getInputStream();
            File targetFile = new File(settingsService.getKey("moviePath") + "_tmp/"
                    + multipartFile.getOriginalFilename());
            FileUtils.copyInputStreamToFile(fileStream, targetFile);

            UploadFile uploadFile = uploadFileDao.getByHash(targetFile.hashCode());
            try {
                uploadFile.getHash();
            } catch (NullPointerException e) {
                uploadFile = new UploadFile();
            }
            uploadFile.setFilename(targetFile.getName());
            uploadFile.setSize(multipartFile.getSize());
            uploadFile.setTimestamp(new Timestamp(new Date().getTime()));
            uploadFile.setHash(targetFile.hashCode());
            uploadFile.setCompleted(true);
            uploadFile.setUser(userAuthService.getUser(request).getUser());
            uploadFile.setVideoType(VideoType.UNDEFINED);
            uploadFile.setReady(false);
            uploadFileDao.save(uploadFile);

            return "redirect:/upload?uploading";
        }
        return "redirect:/";
    }

    @PostMapping("edit/{uploadId}")
    public String changeAttributes(@PathVariable("uploadId") Long uploadId,
                                   @RequestParam("name") String newName,
                                   @RequestParam("videoType") String videoType,
                                   HttpServletRequest request) {
        if (userAuthService.isAdministrator(request)) {
            userAuthService.log(this.getClass(), request);
            edit(uploadId, newName, videoType);
            return "redirect:/upload?name";
        }
        return "redirect:/";
    }

    private void edit(Long uploadId, String newName, String videoType) {
        File uploadDir = new File(settingsService.getKey("moviePath") + "_tmp");

        UploadFile uploadFile = uploadFileDao.getById(uploadId);

        for (File file : uploadDir.listFiles()) {
            if (file.hashCode() == uploadFile.getHash()) {
                try {
                    File newFile = new File(file.getParent(), newName);
                    Files.move(file.toPath(), newFile.toPath());
                    uploadFile.setFilename(newFile.getName());
                    uploadFile.setVideoType(VideoType.getType(videoType));
                    uploadFile.setHash(newFile.hashCode());
                    regexAttributes(uploadFile);
                    uploadFileDao.save(uploadFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void regexAttributes(UploadFile uploadFile) {

        if (uploadFile.getFilename().endsWith(".mkv")) {
            uploadFile.setMimetype("mkv");
        } else if (uploadFile.getFilename().endsWith(".mp4")) {
            uploadFile.setMimetype("mp4");
        } else if (uploadFile.getFilename().endsWith(".avi")) {
            uploadFile.setMimetype("avi");
        }

        String filename = uploadFile.getFilename()
                .replace(".mp4", "")
                .replace(".mkv", "")
                .replace(".avi", "");

        try {
            switch (uploadFile.getVideoType()) {
                case "movie":
                    String[] splits = filename.split(" ");
                    uploadFile.setQuality(splits[splits.length - 1]);
                    uploadFile.setYear(Integer.valueOf(splits[splits.length - 2]));
                    uploadFile.setTitle(filename.replace(" " + uploadFile.getYear()
                            + " " + uploadFile.getQuality(), ""));
                    uploadFile.setReady(true);
                    break;
                case "episode":
                    uploadFile.setTitle(getName(filename) + " " + getEpisodeStr(filename));
                    uploadFile.setYear(Integer.valueOf(getYear(filename)));
                    uploadFile.setQuality(getQuality(filename));
                    uploadFile.setReady(true);
                    break;
                default:
                    uploadFile.setReady(false);
                    break;
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
            uploadFile.setTitle(null);
            uploadFile.setYear(null);
            uploadFile.setQuality(null);
            uploadFile.setVideoType(VideoType.UNDEFINED);
            uploadFile.setReady(false);
            return;
        }
    }

    private String getName(String fileName) {
        return fileName.replace(" " + getEpisodeStr(fileName) + " " + getYear(fileName) + " "
                + getQuality(fileName), "");
    }

    private String getEpisodeStr(String s) {
        String[] splits = s.split(" ");
        return splits[splits.length - 3];
    }

    private String getYear(String s) {
        String[] splits = s.split(" ");
        return splits[splits.length - 2];
    }

    private String getQuality(String s) {
        String[] splits = s.split(" ");
        return splits[splits.length - 1];
    }


    @PostMapping("accept/{uploadId}")
    public String acceptMovie(@PathVariable("uploadId") Long uploadId, HttpServletRequest request) {
        if (userAuthService.isAdministrator(request)) {
            userAuthService.log(this.getClass(), request);
            File file = new File(settingsService.getKey("moviePath") + "_tmp");

            UploadFile uploadFile = uploadFileDao.getById(uploadId);

            for (File fi : file.listFiles()) {
                if (fi.hashCode() == uploadFile.getHash()) {
                    try {

                        if (uploadFile.getVideoType().equals("movie")) {
                            File movedFile = new File(
                                    settingsService.getKey("moviePath") + "/" + fi.getName());
                            Files.move(fi.toPath(), movedFile.toPath());
                            uploadFileDao.delete(uploadFile);
                        } else if (uploadFile.getVideoType().equals("episode")) {
                            File movedFile = new File(
                                    settingsService.getKey("seriePath") + "/" + fi.getName());
                            Files.move(fi.toPath(), movedFile.toPath());
                            uploadFileDao.delete(uploadFile);
                        }
                        return "redirect:/upload?added";
                    } catch (FileAlreadyExistsException e) {
                        return "redirect:/upload?exists";
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return "redirect:/";
    }

    @PostMapping("delete/{uploadId}")
    public String deleteFile(@PathVariable("uploadId") Long uploadId, HttpServletRequest request) {
        if (userAuthService.isAdministrator(request)) {
            userAuthService.log(this.getClass(), request);

            UploadFile uploadFile = uploadFileDao.getById(uploadId);

            File file = new File(settingsService.getKey("moviePath") + "_tmp");
            for (File fi : file.listFiles()) {
                if (fi.hashCode() == uploadFile.getHash()) {
                    if (fi.delete()) {
                        uploadFileDao.delete(uploadFile);
                        return "redirect:/upload?deleted=true";
                    } else {
                        return "redirect:/upload?deleted=false";
                    }
                }
            }
        }
        return "redirect:/";
    }

    @PostMapping("scan")
    public String scan(HttpServletRequest request) {
        if (userAuthService.isAdministrator(request)) {
            userAuthService.log(this.getClass(), request);
            scan();
            return "redirect:/upload?scan";
        }
        return "redirect:/";
    }


    @PostMapping("nameRegex")
    public String nameRegex(HttpServletRequest request) {
        if (userAuthService.isAdministrator(request)) {
            userAuthService.log(this.getClass(), request);

            List<UploadFile> files = uploadFileDao.getAll();

            for (UploadFile uploadFile : files) {
                if (!uploadFile.isCorrect() && !uploadFile.getFilename().endsWith(".filename")) {
                    edit(uploadFile.getId(),
                            fileNameService.getFileCorrected(uploadFile.getFilename()),
                            "movie");
                }
            }

            return "redirect:/upload";
        }
        return "redirect:/";

    }

    private void scan() {
        File[] files = new File(settingsService.getKey("moviePath") + "_tmp").listFiles();

        for (File fi : files) {
            if (!uploadFileDao.exists(fi.hashCode())) {
                UploadFile uploadFile = new UploadFile();
                uploadFile.setFilename(fi.getName());
                uploadFile.setHash(fi.hashCode());
                uploadFile.setCompleted(true);
                uploadFile.setSize(fi.length());
                uploadFile.setVideoType(VideoType.UNDEFINED);
                uploadFile.setTimestamp(new Timestamp(new Date().getTime()));
                uploadFile.setReady(false);
                uploadFileDao.save(uploadFile);
            }
        }

        for (UploadFile uploadFile : uploadFileDao.getAll()) {
            boolean fileExists = false;
            for (File fi : files) {
                if (fi.hashCode() == uploadFile.getHash()) {
                    fileExists = true;
                }
            }

            if (!fileExists) {
                uploadFileDao.delete(uploadFile);
            }
        }
    }

    void acceptFile() {

    }
}
