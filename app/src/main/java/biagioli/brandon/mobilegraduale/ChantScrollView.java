package biagioli.brandon.mobilegraduale;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.ScrollView;

/**
 * Created by Brandon on 3/16/2017.
 */
public class ChantScrollView extends ScrollView {

    public ChantScrollView(Context context,int chantID) {
        super(context);
        setFillViewport(true);

        GregorianChantView chantView = new GregorianChantView(context, chantID);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        chantView.setLayoutParams(params);
        addView(chantView);
    }
}
