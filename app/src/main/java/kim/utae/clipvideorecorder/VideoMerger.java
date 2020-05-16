package kim.utae.clipvideorecorder;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.coremedia.iso.boxes.Container;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by Utae on 2015-11-29.
 */
public class VideoMerger extends AsyncTask<File, Void, Boolean>{

    private WeakReference<Activity> activityRef;
    private WeakReference<ProgressBar> progressBarRef;
    private String duration;

    VideoMerger(Activity activity, ProgressBar progressBar, String duration) {
        this.activityRef = new WeakReference<>(activity);
        this.progressBarRef = new WeakReference<>(progressBar);
        this.duration = duration;
    }

    @Override
    protected Boolean doInBackground(File... params) {

        File dir = params[0];

        File[] files = dir.listFiles();

        try{

            Arrays.sort(files, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));

            ArrayList<Movie> movies = new ArrayList<>();

            List<Track> videoTracks = new LinkedList<>();
            List<Track> audioTracks = new LinkedList<>();

            for(File file : files){
                Movie movie = MovieCreator.build(file.getPath());
                for(Track track : movie.getTracks()){
                    if(track.getHandler().equals("vide")){
                        videoTracks.add(track);
                    }
                    if(track.getHandler().equals("soun")){
                        audioTracks.add(track);
                    }
                }
            }

            Movie merge = new Movie();

            if(audioTracks.size() > 0){
                merge.addTrack(new AppendTrack(audioTracks.toArray(new Track[audioTracks.size()])));
            }
            if(videoTracks.size() > 0){
                merge.addTrack(new AppendTrack(videoTracks.toArray(new Track[videoTracks.size()])));
            }

            Container container = new DefaultMp4Builder().build(merge);

            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, dir.getName() + ".mp4");
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Video.Media.IS_PENDING, 1);
            }

            ContentResolver contentResolver = activityRef.get().getContentResolver();
            Uri uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);

            if(uri != null){
                ParcelFileDescriptor pfd = contentResolver.openFileDescriptor(uri, "w", null);

                if(pfd != null) {
                    FileChannel fileChannel = new FileOutputStream(pfd.getFileDescriptor()).getChannel();

                    container.writeContainer(fileChannel);

                    fileChannel.close();

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear();
                        values.put(MediaStore.Video.Media.IS_PENDING, 0);
                        contentResolver.update(uri, values, null, null);
                    }
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected void onPostExecute(Boolean sucess) {
        progressBarRef.get().setVisibility(View.GONE);
        if(sucess){
            Toast.makeText(activityRef.get(), "video saved", Toast.LENGTH_SHORT).show();

        }else{
            Toast.makeText(activityRef.get(), "failed", Toast.LENGTH_SHORT).show();
        }
    }

}
