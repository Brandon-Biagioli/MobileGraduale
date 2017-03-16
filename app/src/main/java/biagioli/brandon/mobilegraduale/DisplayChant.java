package biagioli.brandon.mobilegraduale;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class DisplayChant extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        int chantID = intent.getIntExtra("MOBILEGRADUALE_CHANT",R.string.Error_Message_Chant);
        setContentView(new ChantScrollView(this, chantID));
    }
}
