package com.clyde.hobart.rubikssolver;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.clyde.hobart.rubikssolver.utilities.CubeColor;
import com.clyde.hobart.rubikssolver.utilities.FilenameDialogFragment;
import com.clyde.hobart.rubikssolver.utilities.Move;
import com.clyde.hobart.rubikssolver.utilities.SelectSizeDialogFragment;
import com.clyde.hobart.rubikssolver.utilities.Solver_1_3;

import java.io.File;
import java.util.LinkedList;

import jxl.Sheet;
import jxl.Workbook;
import jxl.format.Colour;

public class NetEditorActivity extends AppCompatActivity {
    private static final String TAG ="NetEditorActivity";
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 0;
    public static final String SIZE_TAG = "size", MOVES_TAG = "moves", COLORS_TAG = "colors", MOVE_TAG = "move", FILENAME_TAG = "filename";
    private ProgressBar mNetPB;
    private String filename;
    private TableLayout mNetTL;
    private View mLeftFaceV, mFrontFaceV, mRightFaceV, mBackFaceV, mUpFaceV, mDownFaceV;
    private char[][] colors;
    private int size;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_net_editor);
        mNetTL = (TableLayout) findViewById(R.id.tl_net);
        mNetPB = (ProgressBar) findViewById(R.id.pb_net);
        mLeftFaceV = findViewById(R.id.v_left_face);
        mFrontFaceV = findViewById(R.id.v_front_face);
        mRightFaceV = findViewById(R.id.v_right_face);
        mBackFaceV = findViewById(R.id.v_back_face);
        mUpFaceV = findViewById(R.id.v_up_face);
        mDownFaceV = findViewById(R.id.v_down_face);
        size = getResources().getInteger(R.integer.default_cols) / 4;
        colors = new char[3 * size][4 * size];
        for(int r = 0; r < 3 * size; r++)
            for(int c = 0; c < 4 * size; c++)
                colors[r][c] = 'X';
        colors[0][0] = 'W';
        resetNet();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.menu_net_editor, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int menuItemId = item.getItemId();
        if(menuItemId == R.id.action_color)
        {
            PopupMenu actionColorPopup = new PopupMenu(this, findViewById(menuItemId));
            actionColorPopup.inflate(R.menu.menu_color_popup);
            char selected = colors[0][0];
            if(selected == 'W')
                actionColorPopup.getMenu().getItem(0).setChecked(true);
            else if(selected == 'Y')
                actionColorPopup.getMenu().getItem(1).setChecked(true);
            else if(selected == 'R')
                actionColorPopup.getMenu().getItem(2).setChecked(true);
            else if(selected == 'O')
                actionColorPopup.getMenu().getItem(3).setChecked(true);
            else if(selected == 'B')
                actionColorPopup.getMenu().getItem(4).setChecked(true);
            else if(selected == 'G')
                actionColorPopup.getMenu().getItem(5).setChecked(true);
            actionColorPopup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener()
            {
                @Override
                public boolean onMenuItemClick(MenuItem item)
                {
                    item.setChecked(true);
                    colors[0][0] = item.getTitle().charAt(0);
                    ((GradientDrawable)((ConstraintLayout)((TableRow)mNetTL.getChildAt(0)).getChildAt(0)).getChildAt(0).getBackground()).setColor(CubeColor.toCubeColor(colors[0][0]).getColor());
                    return true;
                }
            });
            actionColorPopup.show();
        }
        else if(menuItemId == R.id.action_clear)
        {
            resetNet();
        }
        else if(menuItemId == R.id.action_size)
        {
            SelectSizeDialogFragment sSDF = new SelectSizeDialogFragment();
            sSDF.show(getFragmentManager(), "Select Size Dialog");
        }
        else if(menuItemId == R.id.action_solve)
        {
            SolveCubeTask solveCubeTask = new SolveCubeTask(this);
            solveCubeTask.execute();
        }
        else if(menuItemId == R.id.action_load)
        {
            FilenameDialogFragment lFDF = new FilenameDialogFragment();
            lFDF.show(getFragmentManager(), "Enter Filename Dialog");
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        colors[0][0] = 'W';
        resetNet();
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if(permissions.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
        {
            LoadFileTask loadFileTask = new LoadFileTask(this);
            loadFileTask.execute();
        }
        else
            Toast.makeText(this, "Cannot load save without permission", Toast.LENGTH_SHORT).show();
    }
    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        outState.putInt(SIZE_TAG, size);
        outState.putString(COLORS_TAG, new Solver_1_3(size, colors).getColorsString());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState)
    {
        size = savedInstanceState.getInt(SIZE_TAG);
        resetNet();
        Solver_1_3 solver = new Solver_1_3(size);
        solver.setColors(savedInstanceState.getString(COLORS_TAG));
        colors = solver.getColors();
        restoreCellColor(0, 0);
        for(int r = 0; r < size; r++)
            for(int c = size; c < 2 * size; c++)
                restoreCellColor(r, c);
        for(int r = size; r < 2 * size; r++)
            for(int c = 0; c < 4 * size; c++)
                restoreCellColor(r, c);
        for(int r = 2 * size; r < 3 * size; r++)
            for(int c = size; c < 2 * size; c++)
                restoreCellColor(r, c);
    }
    private void restoreCellColor(int r, int c)
    {
        ((GradientDrawable)((ConstraintLayout)((TableRow)mNetTL.getChildAt(r)).getChildAt(c)).getChildAt(0).getBackground()).setColor(CubeColor.toCubeColor(colors[r][c]).getColor());
    }

    /**
     * Resets mNetTL to the default colorPrimary value
     */
    public void resetNet()
    {
        char selected = colors[0][0];
        colors = new char[3 * size][4 * size];
        for(int r = 0; r < 3 * size; r++)
            for(int c = 0; c < 4 * size; c++)
                colors[r][c] = 'X';
        colors[0][0] = selected;
        mNetTL.removeAllViews();
        TableRow row = new TableRow(this);
        row.addView(inflateCell(true, 0, 0));
        for(int c = 1; c < size; c++)
            row.addView(new TextView(this));
        for(int c = size; c < 2 * size; c++)
            row.addView(inflateCell(false, 0, c));
        mNetTL.addView(row);
        for(int r = 1; r < size; r++)
        {
            row = new TableRow(this);
            row.setBackgroundColor(Color.argb(0, 0, 0, 0));
            for(int c = 0; c < size; c++)
                row.addView(new TextView(this));
            for(int c = size; c < 2 * size; c++)
                row.addView(inflateCell(false, r, c));
            mNetTL.addView(row);
        }
        for(int r = size; r < 2 * size; r++)
        {
            row = new TableRow(this);
            row.setBackgroundColor(Color.argb(0, 0, 0, 0));
            for(int c = 0; c < 4 * size; c++)
                row.addView(inflateCell(false, r, c));
            mNetTL.addView(row);
        }
        for(int r = 2 * size; r < 3 * size; r++)
        {
            row = new TableRow(this);
            row.setBackgroundColor(Color.argb(0, 0, 0, 0));
            for(int c = 0; c < size; c++)
                row.addView(new TextView(this));
            for(int c = size; c < 2 * size; c++)
                row.addView(inflateCell(false, r, c));
            mNetTL.addView(row);
        }
        resizeFrameLayout(mLeftFaceV);
        resizeFrameLayout(mFrontFaceV);
        resizeFrameLayout(mRightFaceV);
        resizeFrameLayout(mBackFaceV);
        resizeFrameLayout(mUpFaceV);
        resizeFrameLayout(mDownFaceV);
    }
    /**
     * @param isSelected boolean containing truth of statement "the returned View will be the color selected cell
     * @param row int row of the View to return
     * @param col int col of the View to return
     * @return View (ConstraintLayout) containing button with the proper click response and corner radius determined by isSelected
     */
    private View inflateCell(boolean isSelected, final int row, final int col)
    {
        ConstraintLayout conLay = (ConstraintLayout) getLayoutInflater().inflate(R.layout.cell_button_layout, null);
        Button button = (Button)conLay.getChildAt(0);
        button.setBackgroundResource(R.drawable.button_background);
        if(isSelected)
        {
            button.setBackgroundResource(R.drawable.selection_background);
            GradientDrawable buttonGradDraw = (GradientDrawable)button.getBackground();
            buttonGradDraw.setCornerRadius(getResources().getDimension(R.dimen.selection_radius));
            buttonGradDraw.setColor(CubeColor.toCubeColor(colors[0][0]).getColor());
            button.setClickable(false);
        }
        else
        {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    colors[row][col] = colors[0][0];
                    ((GradientDrawable) v.getBackground()).setColor(CubeColor.toCubeColor(colors[0][0]).getColor());
                }
            });
            ((GradientDrawable)button.getBackground()).setColor(CubeColor.toCubeColor(colors[row][col]).getColor());
        }
        return conLay;
    }
    public int getSize()
    {
        return size;
    }
    public void setSize(int s)
    {
        size = s;
    }
    /**
     * AsyncTask to solve the cube. Makes Toast with error message if necessary, otherwise creates new Activity to display the generated moves
     */
    private class SolveCubeTask extends AsyncTask<Void, Void, Object>
    {
        private Context context;
        SolveCubeTask(Context c)
        {
            context = c;
        }
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgressBar();
        }

        @Override
        protected Object doInBackground(Void... params) {
            Solver_1_3 solver = new Solver_1_3(size, colors);
            String response = solver.solveMenuItemPress();
            if(response != null)
                return response;
            else
                return solver;
        }

        @Override
        protected void onPostExecute(Object o)
        {
            hideProgressBar();
            if(o instanceof String)
                Toast.makeText(context, (String)o, Toast.LENGTH_SHORT).show();
            else
            {
                Solver_1_3 solver = (Solver_1_3)o;
                Intent intent = new Intent(context, MoveViewerActivity.class);
                intent.putExtra(SIZE_TAG, solver.getSize());
                intent.putExtra(MOVES_TAG, solver.getMovesString());
                intent.putExtra(COLORS_TAG, solver.getColorsString());
                startActivityForResult(intent, 0);
            }
        }
    }
    public void showProgressBar()
    {
        mNetPB.setVisibility(View.VISIBLE);
    }
    public void hideProgressBar()
    {
        mNetPB.setVisibility(View.INVISIBLE);
    }
    public void loadFile(String fn)
    {
        filename = fn;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE))
            {

                Toast.makeText(this, "Requesting external storage permission to save solve data for future retrieval.", Toast.LENGTH_LONG).show();
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_EXTERNAL_STORAGE);
            }
            else
            {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_EXTERNAL_STORAGE);
            }
        }
        else
        {
            LoadFileTask loadFileTask = new LoadFileTask(this);
            loadFileTask.execute();
        }
    }
    /**
     * AsyncTask to load the desired file. Makes Toast with error message if necessary, otherwise creates new Activity to display the loaded moves
     */
    public class LoadFileTask extends AsyncTask<Void, Void, String>
    {
        private Activity activity;
        private LinkedList<Move> movesList;
        private int move;
        public LoadFileTask(Activity a)
        {
            activity = a;
        }

        @Override
        protected void onPreExecute()
        {
            super.onPreExecute();
            mNetPB.setVisibility(View.VISIBLE);
        }

        @Override
        protected String doInBackground(Void... params)
        {
            String error = "";
            Workbook wkbk;
            try
            {
                File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/RubiksSolverSaves");
                wkbk = Workbook.getWorkbook(new File(dir, filename + ".xls"));
                Sheet initState = wkbk.getSheet(0);
                boolean searching = true;
                for(int i = 1; searching; i++)
                    if(initState.getRowView(i).getSize() == 255)
                    {
                        size = (i - 1) / 3;
                        searching = false;
                    }
                colors = new char[size * 3][size * 4];
                for(int r = 0; r < 3 * size; r++)
                    for(int c = 0; c < 4 * size; c++)
                        colors[r][c] = 'X';
                updateColors(0, size, initState);
                updateColors(size, 0, initState);
                updateColors(size, size, initState);
                updateColors(size, size * 2, initState);
                updateColors(size, size * 3, initState);
                updateColors(size * 2, size, initState);
                Sheet moves = wkbk.getSheet(2);
                String totCell = moves.getCell(0, 0).getContents();
                int numMoves = Integer.parseInt(totCell.substring(totCell.indexOf(':') + 1));
                movesList = new LinkedList<>();
                move = 0;
                for(int i = 0; i < numMoves; i++)
                {
                    movesList.add(new Move(moves.getCell(i % 10 + 1, i / 10 + 1).getContents(), size));
                    if(i % 5 == 0 && moves.getCell(i % 10 + 1, i / 10 + 1).getCellFormat().getBackgroundColour().equals(Colour.LIGHT_BLUE))
                        move = i;
                }
            }
            catch(Exception e)
            {
                error += "\nTrouble retrieving file.\n" + e;
            }
            return error;
        }


        @Override
        protected void onPostExecute(String error) {
            mNetPB.setVisibility(View.INVISIBLE);
            if(error.equals(""))
            {
                Solver_1_3 solver = new Solver_1_3(size, colors, movesList);
                Intent intent = new Intent(activity, MoveViewerActivity.class);
                intent.putExtra(SIZE_TAG, size);
                intent.putExtra(MOVES_TAG, solver.getMovesString());
                intent.putExtra(COLORS_TAG, solver.getColorsString());
                intent.putExtra(MOVE_TAG, move);
                intent.putExtra(FILENAME_TAG, filename);
                startActivityForResult(intent, 0);
            }
            else
                Toast.makeText(activity, R.string.load_response_error + error, Toast.LENGTH_LONG).show();
        }
    }
    /**
     * @param c Colour to return char correspondent of
     * @return corresponding char to c
     */
    private static char getCColChar(Colour c)
    {
        if(c == Colour.WHITE)
            return 'W';
        else if(c == Colour.YELLOW)
            return 'Y';
        else if(c == Colour.RED)
            return 'R';
        else if(c == Colour.ORANGE)
            return 'O';
        else if(c == Colour.BLUE)
            return 'B';
        else
            return 'G';
    }
    /**
     * updates char[][] colors with the appropriate char colors within the range of [rMin, rMin + size)[cMin, cMin + size)
     * @param rMin minimum row value
     * @param cMin minimum column value
     */
    private void updateColors(int rMin, int cMin, Sheet initState)
    {
        for(int dr = 0; dr < size; dr++)
            for(int dc = 0; dc < size; dc++)
            {
                colors[rMin + dr][cMin + dc] = getCColChar(initState.getCell(cMin + dc + 1, rMin + dr + 1).getCellFormat().getBackgroundColour());
            }
    }
    private void resizeFrameLayout(View v)
    {
        float faceSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, size * 44, getResources().getDisplayMetrics());
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) v.getLayoutParams();
        params.height = (int)faceSize;
        params.width = (int)faceSize;
        v.setLayoutParams(params);
    }
}
