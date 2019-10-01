package com.dittolive.todo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.jetbrains.annotations.NotNull;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import live.ditto.*;
import live.ditto.android.DefaultAndroidDittoKitDependencies;

public class MainActivity extends AppCompatActivity implements NewTaskDialogFragment.NewTaskDialogListener, TasksAdapter.ItemClickListener {
    private RecyclerView recyclerView = null;
    private RecyclerView.Adapter viewAdapter = null;
    private RecyclerView.LayoutManager viewManager = null;

    private DittoKit ditto = null;
    private DittoCollection collection = null;
    private DittoLiveQuery liveQuery = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Setup the layout
        this.viewManager = new LinearLayoutManager(this);
        final TasksAdapter tasksAdapter = new TasksAdapter(getApplicationContext());
        tasksAdapter.setClickListener(this);
        this.viewAdapter = tasksAdapter;

        this.recyclerView = findViewById(R.id.recyclerView);
        this.recyclerView.setHasFixedSize(true);
        this.recyclerView.setLayoutManager(viewManager);
        this.recyclerView.setAdapter(viewAdapter);

        this.recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        // Create an instance of DittoKit
        DefaultAndroidDittoKitDependencies androidDependencies = new DefaultAndroidDittoKitDependencies(getApplicationContext());
        final DittoKit ditto = new DittoKit(androidDependencies);
        this.ditto = ditto;

        // Set your DittoKit access license
        // The SDK will not work without this!
        ditto.setAccessLicense("<INSERT ACCESS LICENSE>");

        // This starts DittoKit's background synchronization
        ditto.start();

        // Add swipe to delete
        SwipeToDeleteCallback swipeToDeleteCallback = new SwipeToDeleteCallback(this) {
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
                DittoDocument<Map<String, Object>> task = tasksAdapter.tasks().get(viewHolder.getAdapterPosition());
                ditto.getStore().collection("tasks").findByID(task._id).remove();
            }
        };

        // Configure the RecyclerView for swipe to delete
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeToDeleteCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

        // Respond to new task button click
        FloatingActionButton addTaskButton = findViewById(R.id.addTaskButton);
        addTaskButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNewTaskUI();
            }
        });

        // Listen for clicks to mark tasks [in]complete
        tasksAdapter.setClickListener(this);

        // This function will create a "live-query" that will update
        // our RecyclerView
        setupTaskList();

        // This will check if the app has location permissions
        // to fully enable Bluetooth
        checkLocationPermission();
    }

    void setupTaskList() {
        // We will create a long-running live query to keep UI up-to-date
        this.collection = this.ditto.getStore().collection("tasks");

        final TasksAdapter adapter = (TasksAdapter) this.viewAdapter;
        final Activity activity = this;

        class LiveQueryHandler implements DittoLiveQueryCallback {
            @SuppressWarnings("unchecked")
            @Override
            public void update(@NotNull List documents, @NotNull DittoLiveQueryEvent event) {
                final List<DittoDocument<Map<String, Object>>> tasks = (List<DittoDocument<Map<String, Object>>>) documents;
                if (event instanceof DittoLiveQueryEvent.Update) {
                    final DittoLiveQueryEvent.Update updateEvent = (DittoLiveQueryEvent.Update) event;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.set(tasks);
                            adapter.inserts(updateEvent.insertions);
                            adapter.deletes(updateEvent.deletions);
                            adapter.updates(updateEvent.updates);
                            adapter.moves(updateEvent.moves);
                        }
                    });

                } else if (event instanceof DittoLiveQueryEvent.Initial) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adapter.setInitial(tasks);
                        }
                    });

                }
            }
        }

        // We use observe to create a live query and a subscription to sync this query with other devices
        this.liveQuery = collection.findAll().sort("dateCreated", true).observe(new LiveQueryHandler());
    }

    void checkLocationPermission() {
        // On Android, parts of Bluetooth LE and WiFi Direct require location permission
        // Ditto will operate without it but data sync may be impossible in certain scenarios
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // For this app we will prompt the user for this permission every time if it is missing
            // We ignore the result - Ditto will automatically notice when the permission is granted
            String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION};
            ActivityCompat.requestPermissions(this, permissions, 0);
        }
    }

    @Override
    public void onDialogSave(DialogFragment dialog, String task) {
        // Add the task to Ditto
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        String currentDateString = sdf.format(date);
        Map<String, Object> content = new HashMap<>();
        content.put("text", task);
        content.put("isComplete", false);
        content.put("dateCreated", currentDateString);
        this.collection.insert(content);
    }

    @Override
    public void onDialogCancel(DialogFragment dialog) { }

    protected void showNewTaskUI() {
        NewTaskDialogFragment newFragment = NewTaskDialogFragment.newInstance(R.string.add_new_task_dialog_title);
        FragmentManager fm = this.getSupportFragmentManager();
        newFragment.show(fm, "newTask");
    }

    @Override
    public void onItemClick(DittoDocument<Map<String, Object>> task) {
        class DocumentUpdater implements DittoSingleMutableDocumentUpdater {
            @Override
            public void update(@NotNull DittoMutableDocument doc) {
                DittoMutableDocument<Map<String, Object>> mutableDoc = (DittoMutableDocument<Map<String, Object>>) doc;
                mutableDoc.get("isComplete").set(!mutableDoc.get("isComplete").getBooleanValue());
            }
        }

        ditto.getStore().collection("tasks").findByID(task._id).update(new DocumentUpdater());
    }
}

class TasksAdapter extends RecyclerView.Adapter<TasksAdapter.TaskViewHolder> {
    private List<DittoDocument<Map<String, Object>>> tasks = new ArrayList<>();
    private LayoutInflater inflater;
    private ItemClickListener clickListener;

    TasksAdapter(Context context) {
        this.inflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = inflater.inflate(R.layout.task_view, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final TaskViewHolder holder, int position) {
        DittoDocument<Map<String, Object>> task = tasks.get(position);
        holder.textView.setText(task.get("text").getStringValue());
        holder.checkBoxView.setChecked(task.get("isComplete").getBooleanValue());
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // NOTE: Cannot use position as this is not accurate based on async updates
                clickListener.onItemClick(tasks.get(holder.getAdapterPosition()));
            }
        });
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        TextView textView;
        CheckBox checkBoxView;

        TaskViewHolder(View view) {
            super(view);
            this.textView = view.findViewById(R.id.taskTextView);
            this.checkBoxView = view.findViewById(R.id.taskCheckBox);
        }
    }

    void setClickListener(ItemClickListener itemClickListener) {
        this.clickListener = itemClickListener;
    }

    public interface ItemClickListener {
        void onItemClick(DittoDocument<Map<String, Object>> task);
    }

    List<DittoDocument<Map<String, Object>>> tasks() {
        return this.tasks;
    }

    int set(List<DittoDocument<Map<String, Object>>> tasksToSet) {
        this.tasks.clear();
        this.tasks.addAll(tasksToSet);
        return this.tasks.size();
    }

    int inserts(List<Integer> indexes) {
        for (int i = 0; i < indexes.size(); i ++) {
            this.notifyItemRangeInserted(indexes.get(i), 1);
        }
        return this.tasks.size();
    }

    int deletes(List<Integer> indexes) {
        for (int i = 0; i < indexes.size(); i ++) {
            this.notifyItemRangeRemoved(indexes.get(i), 1);
        }
        return this.tasks.size();
    }

    int updates(List<Integer> indexes) {
        for (int i = 0; i < indexes.size(); i ++) {
            this.notifyItemRangeChanged(indexes.get(i), 1);
        }
        return this.tasks.size();
    }

    void moves(List<DittoLiveQueryMove> moves) {
        for (int i = 0; i < moves.size(); i ++) {
            this.notifyItemMoved(moves.get(i).getFrom(), moves.get(i).getTo());
        }
    }

    int setInitial(List<DittoDocument<Map<String, Object>>> tasksToSet) {
        this.tasks.addAll(tasksToSet);
        this.notifyDataSetChanged();
        return this.tasks.size();
    }

}

// Swipe to delete based on https://medium.com/@kitek/recyclerview-swipe-to-delete-easier-than-you-thought-cff67ff5e5f6
abstract class SwipeToDeleteCallback extends ItemTouchHelper.SimpleCallback {
    private Drawable deleteIcon;
    private int intrinsicWidth;
    private int intrinsicHeight;
    private ColorDrawable background = new ColorDrawable();
    private int backgroundColor = Color.parseColor("#f44336");
    private Paint clearPaint = new Paint();

    SwipeToDeleteCallback(Context context) {
        super(0, ItemTouchHelper.LEFT);
        deleteIcon = context.getDrawable(android.R.drawable.ic_menu_delete);
        intrinsicWidth = deleteIcon.getIntrinsicWidth();
        intrinsicHeight = deleteIcon.getIntrinsicHeight();
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        View itemView = viewHolder.itemView;
        int itemHeight = itemView.getBottom() - itemView.getTop();
        boolean isCanceled = dX == 0f && !isCurrentlyActive;

        if (isCanceled) {
            clearCanvas(c, itemView.getRight() + dX, (float) itemView.getTop(), (float) itemView.getRight(), (float) itemView.getBottom());
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            return;
        }

        // Draw the red delete background
        background.setColor(backgroundColor);
        background.setBounds(itemView.getRight() + (int) dX, itemView.getTop(), itemView.getRight(), itemView.getBottom());
        background.draw(c);

        // Calculate position of delete icon
        int deleteIconTop = itemView.getTop()+ (itemHeight - intrinsicHeight) / 2;
        int deleteIconMargin = (itemHeight - intrinsicHeight) / 2;
        int deleteIconLeft = itemView.getRight() - deleteIconMargin - intrinsicWidth;
        int deleteIconRight = itemView.getRight() - deleteIconMargin;
        int deleteIconBottom = deleteIconTop + intrinsicHeight;

        // Draw the delete icon
        deleteIcon.setBounds(deleteIconLeft, deleteIconTop, deleteIconRight, deleteIconBottom);
        deleteIcon.setTint(Color.parseColor("#ffffff"));
        deleteIcon.draw(c);

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    private void clearCanvas(Canvas c, Float left, Float top, Float right, Float bottom) {
        c.drawRect(left, top, right, bottom, clearPaint);
    }
}
