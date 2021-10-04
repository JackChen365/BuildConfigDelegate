package jack.android.buildconfig;

import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.android.BuildConfigDelegate;
import com.android.library.BuildConfig;
import com.android.library.HttpServer;
import java.util.Set;

/**
 * Created on 2021/10/4.
 *
 * @author Jack Chen
 * @email bingo110@126.com
 */
public class MainActivity extends AppCompatActivity {
    private static final String APPLICATION_ID = BuildConfig.TARGET_APPLICATION_ID;

    static {
        System.out.println(APPLICATION_ID);
    }

    @Override protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        System.out.println(BuildConfig.TARGET_APPLICATION_ID);
        final Set<String> flavorSet = BuildConfigDelegate.getFlavorSet();
        BuildConfigDelegate.setCurrentFlavor("androidTVStagingDebug");
        final TextView textView = findViewById(R.id.text_view);
        textView.append("AD_SDK_ID:" + com.android.library.BuildConfig.TARGET_APPLICATION_ID);
        textView.append("\n");
        textView.append("Server:" + HttpServer.getServer() + "\nUI:" + HttpServer.getUIUrl());
    }
}
