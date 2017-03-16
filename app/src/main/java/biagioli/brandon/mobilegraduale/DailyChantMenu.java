package biagioli.brandon.mobilegraduale;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

/**
 * Created by Brandon on 3/16/2017.
 */
public class DailyChantMenu extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_propers_menu);
    }

    public void displayDailyIntroit(View view) {
        Intent intent = new Intent(this, DisplayChant.class);
        intent.putExtra("MOBILEGRADUALE_CHANT",R.string.Introit_Ad_te_levavi);
        startActivity(intent);
    }

    public void displayDailyGradual(View view) {
        Intent intent = new Intent(this, DisplayChant.class);
        intent.putExtra("MOBILEGRADUALE_CHANT",R.string.Gradual_Universi_qui_te_exspectant);
        startActivity(intent);
    }

    public void displayDailyAlleluia(View view) {
        Intent intent = new Intent(this, DisplayChant.class);
        intent.putExtra("MOBILEGRADUALE_CHANT",R.string.Alleluia_Ostende_nobis);
        startActivity(intent);
    }

    public void displayDailyOffertory(View view) {
        Intent intent = new Intent(this, DisplayChant.class);
        intent.putExtra("MOBILEGRADUALE_CHANT",R.string.Offertory_Ad_te_Domine_levavi);
        startActivity(intent);
    }

    public void displayDailyCommunion(View view) {
        Intent intent = new Intent(this, DisplayChant.class);
        intent.putExtra("MOBILEGRADUALE_CHANT",R.string.Communion_Dominus_dabit_benignitatem);
        startActivity(intent);
    }
}
