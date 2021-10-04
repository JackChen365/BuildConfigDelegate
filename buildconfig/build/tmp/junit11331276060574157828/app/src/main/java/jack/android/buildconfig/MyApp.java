package jack.android.buildconfig;

import android.app.Application;
import android.content.Context;
import com.android.BuildConfigDelegate;

/**
 * Created on 2021/10/4.
 * @author Jack Chen
 */
public class MyApp extends Application {
    @Override protected void attachBaseContext(final Context base) {
        super.attachBaseContext(base);
        try {
            BuildConfigDelegate.initialModuleBuildConfig(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
