package com.coolweather.android;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.coolweather.android.db.City;
import com.coolweather.android.db.Country;
import com.coolweather.android.db.Province;
import com.coolweather.android.util.HttpUtil;
import com.coolweather.android.util.ParseUtil;

import org.jetbrains.annotations.NotNull;
import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ChooseAreaFragment extends Fragment {
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTRY = 2;
    private int currentLevel;
    private Province selectProvince;
    private City selectCity;
    private List<Province> provinceList;
    private List<City> cityList;
    private List<Country> countryList;
    private ProgressDialog progressDialog;
    private ArrayAdapter<String> adapter;
    private List<String> list=new ArrayList<>();
    private TextView tv_title;
    private Button bt_back;
    private ListView lv_show;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.choose_area, container, false);
        tv_title = view.findViewById(R.id.tv_title);
        bt_back = view.findViewById(R.id.bt_back);
        lv_show = view.findViewById(R.id.lv_show);
        adapter = new ArrayAdapter<>(getContext(), R.layout.listview_item, list);
        lv_show.setAdapter(adapter);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        lv_show.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentLevel == LEVEL_PROVINCE) {
                   selectProvince= provinceList.get(position);
                   queryCity();
                }else if(currentLevel==LEVEL_CITY){
                    selectCity=cityList.get(position);
                    queryCountry();
                }else if(currentLevel==LEVEL_COUNTRY){
                    String weatherId=countryList.get(position).getWeatherId();
                    if(getActivity()instanceof MainActivity){
                    Intent intent=new Intent(getActivity(),WeatherActivity.class);
                    intent.putExtra("weather_id",weatherId);
                    startActivity(intent);
                    getActivity().finish();
                    }else if(getActivity()instanceof WeatherActivity){
                        WeatherActivity activity = (WeatherActivity) getActivity();
                        activity.drawerLayout.closeDrawer(GravityCompat.START);
                        activity.swipeRefresh.setRefreshing(true);
                        activity.requestWeather(weatherId);

                    }
                }
            }
        });
        bt_back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(currentLevel==LEVEL_CITY){
                    queryProvince();
                }else if(currentLevel==LEVEL_COUNTRY){
                    queryCity();
                }
            }
        });
        queryProvince();
    }

    private void queryProvince() {
        tv_title.setText("中国");
        bt_back.setVisibility(View.GONE);
        provinceList = DataSupport.findAll(Province.class);
        if (provinceList.size() > 0) {
            list.clear();
            for (Province province : provinceList) {
                list.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            lv_show.setSelection(0);
            currentLevel = LEVEL_PROVINCE;
        } else {
            String address = "http://guolin.tech/api/china";
            queryFromServer(address, "province");


        }

    }

    private void queryCity() {
        tv_title.setText(selectProvince.getProvinceName());
        bt_back.setVisibility(View.VISIBLE);
        cityList = DataSupport.where("provinceId=?", String.valueOf(selectProvince.getId())).find(City.class);
        if (cityList.size() > 0) {
            list.clear();
            for (City city : cityList) {
                list.add(city.getCityName());
            }
            adapter.notifyDataSetChanged();
            lv_show.setSelection(0);
            currentLevel = LEVEL_CITY;
        } else {
            int provinceCode = selectProvince.getProvinceCode();
            String address = "http://guolin.tech/api/china/" + provinceCode;
            queryFromServer(address, "city");
        }
    }

    private void queryCountry() {
        tv_title.setText(selectCity.getCityName());
        bt_back.setVisibility(View.VISIBLE);
        countryList = DataSupport.where("cityId=?", String.valueOf(selectCity.getId())).find(Country.class);
        if (countryList.size() > 0) {
            list.clear();
            for (Country country : countryList) {
                list.add(country.getCountryName());
            }
            adapter.notifyDataSetChanged();
            lv_show.setSelection(0);
            currentLevel = LEVEL_COUNTRY;
        } else {
            int provinceCode = selectProvince.getProvinceCode();
            int cityCode = selectCity.getCityCode();
            String address = "http://guolin.tech/api/china/" + provinceCode + "/" + cityCode;
            queryFromServer(address, "country");
        }
    }

    private void queryFromServer(String address,  final String type) {
        showProgressDialog();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String s = response.body().string();
                boolean result = false;
                if ("province".equals(type)) {
                    result = ParseUtil.addProvince(s);
                } else if ("city".equals(type)) {
                    result = ParseUtil.addCity(s, selectProvince.getId());
                } else if ("country".equals(type)) {
                    result = ParseUtil.addCountry(s, selectCity.getId());
                }
                if (result) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if ("province".equals(type)) {
                                queryProvince();
                            } else if ("city".equals(type)) {
                                queryCity();
                            } else if ("country".equals(type)) {
                                queryCountry();
                            }
                        }
                    });
                }
            }

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }


        });

    }

    private void showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("正在加载...");
            progressDialog.setCanceledOnTouchOutside(false);

        }
        progressDialog.show();
    }

    private void closeProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }
}
