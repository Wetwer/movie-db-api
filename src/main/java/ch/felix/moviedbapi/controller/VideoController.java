package ch.felix.moviedbapi.controller;

import ch.felix.moviedbapi.data.repository.EpisodeRepository;
import ch.felix.moviedbapi.data.repository.MovieRepository;
import ch.felix.moviedbapi.service.MultipartFileSender;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.file.Paths;

@Controller
@RequestMapping("video")
public class VideoController {

    private MovieRepository movieRepository;
    private EpisodeRepository episodeRepository;

    public VideoController(MovieRepository movieRepository, EpisodeRepository episodeRepository) {
        this.movieRepository = movieRepository;
        this.episodeRepository = episodeRepository;
    }

    @GetMapping(value = "/movie/{movieId}")
    public void getMovie(@PathVariable("movieId") String movieId,
                         HttpServletRequest request, HttpServletResponse response) throws Exception {
        MultipartFileSender.fromPath(Paths.get(movieRepository.findMovieById(Long.valueOf(movieId)).getVideoPath()))
                .with(request)
                .with(response)
                .serveResource();
    }

    @GetMapping(value = "/episode/{episodeId}")
    public void getEpisode(@PathVariable("episodeId") String episodeId,
                           HttpServletRequest request, HttpServletResponse response) throws Exception {
        MultipartFileSender.fromPath(Paths.get(episodeRepository.findEpisodeById(Long.valueOf(episodeId)).getPath()))
                .with(request)
                .with(response)
                .serveResource();
    }
}
