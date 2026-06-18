package uz.vocabexam.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.file.*;

@Configuration
public class AppConfig implements WebMvcConfigurer {
    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Value("${app.pdf-dir:pdfs}")
    private String pdfDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try {
            Path uploadPath = Paths.get(uploadDir).toAbsolutePath();
            Path pdfPath    = Paths.get(pdfDir).toAbsolutePath();
            Files.createDirectories(uploadPath);
            Files.createDirectories(pdfPath);
            registry.addResourceHandler("/uploads/**").addResourceLocations("file:" + uploadPath + "/");
            registry.addResourceHandler("/pdfs/**").addResourceLocations("file:" + pdfPath + "/");
        } catch (Exception e) {
            throw new RuntimeException("Upload dirs creation failed", e);
        }
    }
}
