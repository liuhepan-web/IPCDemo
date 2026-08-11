package com.ipc.demo.set;





import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.thingclips.smart.home.sdk.ThingHomeSdk;
import com.thingclips.smart.home.sdk.bean.HomeBean;
import com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Query home list and select current home.
 */
public class HomeListActivity extends AppCompatActivity {

    private HomeAdapter adapter;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(R.string.home_list_title);
        tvEmpty = findViewById(R.id.tvEmpty);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HomeAdapter();
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHomes();
    }

    private void loadHomes() {
        ThingHomeSdk.getHomeManagerInstance().queryHomeList(new IThingGetHomeListCallback() {
            @Override
            public void onSuccess(List<HomeBean> homeBeans) {
                List<HomeBean> list = homeBeans == null ? new ArrayList<>() : homeBeans;
                adapter.setData(list);
                tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                tvEmpty.setText(R.string.home_list_empty);
            }

            @Override
            public void onError(String errorCode, String error) {
                Toast.makeText(HomeListActivity.this,
                        getString(R.string.error_with_code, errorCode, error),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private class HomeAdapter extends RecyclerView.Adapter<HomeAdapter.Holder> {
        private final List<HomeBean> data = new ArrayList<>();

        void setData(List<HomeBean> list) {
            data.clear();
            data.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_simple_row, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            HomeBean bean = data.get(position);
            long current = HomeModel.getCurrentHome(HomeListActivity.this);
            String mark = bean.getHomeId() == current ? " ✓" : "";
            holder.tvTitle.setText(bean.getName() + mark);
            holder.tvSubtitle.setText("ID: " + bean.getHomeId());
            holder.itemView.setOnClickListener(v -> {
                HomeModel.setCurrentHome(HomeListActivity.this, bean.getHomeId());
                Toast.makeText(HomeListActivity.this, R.string.home_selected, Toast.LENGTH_SHORT).show();
                notifyDataSetChanged();
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView tvTitle;
            final TextView tvSubtitle;

            Holder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            }
        }
    }
}
