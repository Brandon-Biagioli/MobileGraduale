package biagioli.brandon.mobilegraduale;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    /**
     * Displays today's propers.
     */
    public void displayDailyPropers(View view) {
        Intent intent = new Intent(this, DailyChantMenu.class);
        startActivity(intent);
    }

    public void displayVotiveMassMenu(View view) {
        Intent intent = new Intent(this, VotiveMassMenu.class);
        startActivity(intent);
    }
}
