package edu.skku.map.myfirstswlab.mapping;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;


import android.content.pm.PackageManager;
import android.os.Bundle;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import net.daum.mf.map.api.MapPOIItem;
import net.daum.mf.map.api.MapPoint;
import net.daum.mf.map.api.MapPolyline;
import net.daum.mf.map.api.MapView;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.LocationManager;

import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;

import net.daum.mf.map.api.MapReverseGeoCoder;

import timber.log.Timber;


public class MainActivity extends AppCompatActivity implements MapView.MapViewEventListener,
        MapView.POIItemEventListener, MapView.CurrentLocationEventListener,
        MapReverseGeoCoder.ReverseGeoCodingResultListener {

    ArrayList<Destination> data = new ArrayList<>();
    ArrayList<Destination> route = new ArrayList<>();
    ArrayAdapter<String> adapter;
    //ArrayAdapter<String> adapter2;
    TextView textView_depart;
    EditText editText;
    ListView listView;
    ListView listView2;
    String query;
    Destination dest;

    //final static String TAG = "MapTag";

    MapView mapView;

    MapPoint currentMapPoint;

    private double longi;
    private double lat;
    boolean isTrackingMode = false;

    private static final int GPS_ENABLE_REQUEST_CODE = 2001;
    private static final int PERMISSIONS_REQUEST_CODE = 100;
    String[] REQUIRED_PERMISSIONS = {Manifest.permission.ACCESS_FINE_LOCATION};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Mapbox.getInstance(this, "MAPBOX_ACCESS_TOKEN");

        //timber 초기화
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }

        Timber.i("hi it is test");

        //xml에서 불러오기
        Button button = findViewById(R.id.button);
        Button routeButton = findViewById(R.id.routebutton);
        //Button btn = findViewById(R.id.Button);
        editText = findViewById(R.id.editText);
        listView = findViewById(R.id.list_view);
        listView2 = findViewById(R.id.list_view2);
        textView_depart = findViewById(R.id.text);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                query = editText.getText().toString();
                Location bthread = new Location(mHandler, query);
                bthread.setDaemon(true);
                bthread.start();
            }
        });

        routeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Toast.makeText(MainActivity.this, "dest: "+ dest, Toast.LENGTH_SHORT).show();
                if(dest == null) {
                    Toast.makeText(MainActivity.this, "목적지를 설정해 주세요" ,Toast.LENGTH_SHORT).show();
                    return;
                }

                Context context = getApplicationContext();
                Point origin = Point.fromLngLat(longi,lat);
                Point destination = Point.fromLngLat(dest.getLongitude(), dest.getLatitude());
                Waypoint cthread = new Waypoint(wHandler,context,origin,destination);
                cthread.setDaemon(true);
                cthread.start();
            }
        });



        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //목적지를 고른다
                dest = data.get(position);
                //목적지를 보여준다
                editText.setText(dest.getName());
                //검색된 리스트를 없앤다
                listView.setAdapter(null);
                Toast.makeText(MainActivity.this,dest.getName() + "로 목적지를 설정합니다.",Toast.LENGTH_SHORT).show();
            }
        });
        initView();

    }



    private void initView() {

        mapView = new MapView(this);

        ViewGroup mapViewContainer = findViewById(R.id.map_view);

        mapViewContainer.addView(mapView); // 지우지 말것, 에러 발생

        mapView.setMapViewEventListener(this);

        mapView.setPOIItemEventListener(this);
        mapView.setCurrentLocationEventListener(this);
        //setCurrentLocationTrackingMode = 지도와 현재위치 좌표 찍어주고 따라다님
        //mapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithHeading);

        //버튼
        Button btn = findViewById(R.id.Button);
        btn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                //setCurrentLocationTrackingMode = 지도와 현재위치 좌표 찍어주고 따라다님
                mapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithHeading);
                Toast.makeText(getApplicationContext(), "현재위치를 재설정했습니다", Toast.LENGTH_SHORT).show();
            }
        });

        if (!checkLocationServicesStatus()) {

            showDialogForLocationServiceSetting();
        } else {

            checkRunTimePermission();
        }

        //중심 설정
        mapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(37.294019, 126.975399), true);

        /*
        //마커 띄우기
        MapPOIItem marker = new MapPOIItem();
        marker.setItemName("Default Marker");
        marker.setTag(0);
        marker.setMapPoint(MapPoint.mapPointWithGeoCoord(37.294019, 126.975399));
        marker.setMarkerType(MapPOIItem.MarkerType.BluePin); // 기본으로 제공하는 BluePin 마커 모양.
        marker.setSelectedMarkerType(MapPOIItem.MarkerType.RedPin);

        mapView.addPOIItem(marker);
        */

        //리스너 등록
        mapView.setPOIItemEventListener(this);
        //onPOIItemSelected(mapView, marker);

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOff);
        mapView.setShowCurrentLocationMarker(false);
    }

    //현재 위치 업데이트(setCurrentLocationEventListener)_
    @Override
    public void onCurrentLocationUpdate(MapView mapView, MapPoint mapPoint, float accuracyInMeters) {

        MapPoint.GeoCoordinate mapPointGeo = mapPoint.getMapPointGeoCoord();
        Timber.i("MapView onCurrentLocationUpdate (%f, %f) accuracy (%f)", mapPointGeo.latitude, mapPointGeo.longitude, accuracyInMeters);


        currentMapPoint = MapPoint.mapPointWithGeoCoord(mapPointGeo.latitude, mapPointGeo.longitude);
        mapView.setMapCenterPoint(currentMapPoint, true);

        lat = mapPointGeo.latitude;
        longi = mapPointGeo.longitude;
        //textView_depart.setText("출발지: " + lat + " : " + longi);
        Timber.d("현재위치 " + lat + " " + longi);


        if(!isTrackingMode) {
            mapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOff);
        }

    }

    @Override
    public void onCurrentLocationDeviceHeadingUpdate(MapView mapView, float v) {

    }

    @Override
    public void onCurrentLocationUpdateFailed(MapView mapView) {

        Timber.i("onCurrentLocationUpdateFailed");
        mapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading);

    }


    @Override
    public void onCurrentLocationUpdateCancelled(MapView mapView) {

        Timber.i("onCurrentLocationUpdateCancelled");
        mapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading);

    }

    @Override
    public void onReverseGeoCoderFoundAddress(MapReverseGeoCoder mapReverseGeoCoder, String s) {
        mapReverseGeoCoder.toString();
        onFinishReverseGeoCoding(s);
    }

    private void onFinishReverseGeoCoding(String s) {
    }

    @Override
    public void onReverseGeoCoderFailedToFindAddress(MapReverseGeoCoder mapReverseGeoCoder) {
        onFinishReverseGeoCoding("Fail");
    }

    //퍼미션 관리
    @Override
    public void onRequestPermissionsResult(int permsRequestCode, @NonNull String[] permissions, @NonNull int[] grandResults) {

        if ( permsRequestCode == PERMISSIONS_REQUEST_CODE && grandResults.length == REQUIRED_PERMISSIONS.length) {

            // 요청 코드가 PERMISSIONS_REQUEST_CODE 이고, 요청한 퍼미션 개수만큼 수신되었다면

            boolean check_result = true;
            // 모든 퍼미션을 허용했는지 체크합니다.

            for (int result : grandResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    check_result = false;
                    break;
                }
            }
            if ( check_result ) {
                Timber.d("start");
                //위치 값을 가져올 수 있음
                mapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithHeading);
            }
            else {
                // 거부한 퍼미션이 있다면 앱을 사용할 수 없는 이유를 설명해주고 앱을 종료합니다.2 가지 경우가 있습니다.
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[0])) {

                    Toast.makeText(MainActivity.this, "퍼미션이 거부되었습니다. 앱을 다시 실행하여 퍼미션을 허용해주세요.", Toast.LENGTH_LONG).show();
                    finish();


                }else {

                    Toast.makeText(MainActivity.this, "퍼미션이 거부되었습니다. 설정(앱 정보)에서 퍼미션을 허용해야 합니다. ", Toast.LENGTH_LONG).show();

                }
            }
        }
    }

    void checkRunTimePermission() {
        int hasFineLocationPermission = ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION);


        if (hasFineLocationPermission == PackageManager.PERMISSION_GRANTED) {

            // 2. 이미 퍼미션을 가지고 있다면
            // 3.  위치 값을 가져올 수 있음
            mapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithHeading);

        } else {  //2. 퍼미션 요청을 허용한 적이 없다면 퍼미션 요청이 필요합니다. 2가지 경우(3-1, 4-1)가 있습니다.

            // 3-1. 사용자가 퍼미션 거부를 한 적이 있는 경우에는
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, REQUIRED_PERMISSIONS[0])) {

                // 3-2. 요청을 진행하기 전에 사용자가에게 퍼미션이 필요한 이유를 설명해줄 필요가 있습니다.
                Toast.makeText(MainActivity.this, "이 앱을 실행하려면 위치 접근 권한이 필요합니다.", Toast.LENGTH_LONG).show();
                // 3-3. 사용자게에 퍼미션 요청을 합니다. 요청 결과는 onRequestPermissionResult에서 수신됩니다.
                ActivityCompat.requestPermissions(MainActivity.this, REQUIRED_PERMISSIONS,
                        PERMISSIONS_REQUEST_CODE);

            } else {
                // 4-1. 사용자가 퍼미션 거부를 한 적이 없는 경우에는 퍼미션 요청을 바로 합니다.
                // 요청 결과는 onRequestPermissionResult에서 수신됩니다.
                ActivityCompat.requestPermissions(MainActivity.this, REQUIRED_PERMISSIONS,
                        PERMISSIONS_REQUEST_CODE);
            }

        }
    }

    //GPS 활성화 메소드
    private void showDialogForLocationServiceSetting() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("위치 서비스 비활성화");
        builder.setMessage("앱을 사용하기 위해서는 위치 서비스가 필요합니다.\n"
                + "위치 설정을 수정하실래요?");
        builder.setCancelable(true);
        builder.setPositiveButton("설정", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                Intent callGPSSettingIntent
                        = new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivityForResult(callGPSSettingIntent, GPS_ENABLE_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("취소", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });
        builder.create().show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GPS_ENABLE_REQUEST_CODE) {//사용자가 GPS 활성 시켰는지 검사
            if (checkLocationServicesStatus()) {
                if (checkLocationServicesStatus()) {

                    Timber.d("onActivityResult : GPS 활성화 되있음");
                    checkRunTimePermission();
                }
            }
        }
    }

    public boolean checkLocationServicesStatus() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        assert locationManager != null;
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

    }
    @Override
    public void onMapViewInitialized(MapView mapView) {
    }

    @Override
    public void onMapViewCenterPointMoved(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewZoomLevelChanged(MapView mapView, int i) {

    }

    //맵 한번 클릭시 호출
    @Override
    public void onMapViewSingleTapped(MapView mapView, MapPoint mapPoint) {
        //Toast.makeText(this, "현재위치 " + lat + " " + longi, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMapViewDoubleTapped(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewLongPressed(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewDragStarted(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewDragEnded(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onMapViewMoveFinished(MapView mapView, MapPoint mapPoint) {

    }

    @Override
    public void onPOIItemSelected(MapView mapView, MapPOIItem poiItem) {

        double[] list = new double[2];
        list[0] =poiItem.getMapPoint().getMapPointGeoCoord().latitude;
        list[1] =poiItem.getMapPoint().getMapPointGeoCoord().longitude;

        //Toast myToast = Toast.makeText(this.getApplicationContext(),String.valueOf(list[0])+ "/" + String.valueOf(list[1]) ,Toast.LENGTH_SHORT);
        //myToast.show();

    }

    @Override
    public void onCalloutBalloonOfPOIItemTouched(MapView mapView, MapPOIItem mapPOIItem) {

    }

    @Override
    public void onCalloutBalloonOfPOIItemTouched(MapView mapView, MapPOIItem mapPOIItem, MapPOIItem.CalloutBalloonButtonType calloutBalloonButtonType) {

    }

    @Override
    public void onDraggablePOIItemMoved(MapView mapView, MapPOIItem mapPOIItem, MapPoint mapPoint) {

    }

    abstract public static class Activity implements MapView.POIItemEventListener {

    }

    private void drawLine(MapView mapView, ArrayList<Destination> route) {
        MapPolyline myPolyLine = new MapPolyline();
        myPolyLine.setLineColor(android.graphics.Color.argb(255,255,0,0));

        for (Destination targetPoint : route) {
            if (targetPoint.getLatitude() == 0.0 || targetPoint.getLongitude() == 0.0) {
                break;
            }
            MapPoint point = MapPoint.mapPointWithGeoCoord(targetPoint.getLatitude(), targetPoint.getLongitude());
            myPolyLine.addPoint(point);
        }

        mapView.addPolyline(myPolyLine);

        Toast.makeText(MainActivity.this,"길을 그렸습니다.",Toast.LENGTH_LONG).show();
    }
    // 핸들러
    @SuppressLint("HandlerLeak")
    Handler mHandler = new Handler() {
        public void handleMessage(Message m) {
            if (m.what == 0) {

                //data 초기화
                data.clear();

                Destination[] list;
                list = (Destination[]) m.obj;
                for (Destination destination : list) {
                    if (destination != null) {
                        data.add(destination);
                    } else {
                        break;
                    }
                }

                ArrayList<String> name = new ArrayList<>();
                for(int i=0;i<data.size();i++){
                    name.add(data.get(i).getName());
                }

                adapter = new ArrayAdapter<>
                        (getApplicationContext(),android.R.layout.simple_list_item_1,name);

                listView.setAdapter(adapter);
            }
        }
    };

    // 핸들러


    @SuppressLint("HandlerLeak")
    Handler wHandler = new Handler() {
        public void handleMessage(Message m) {
            if (m.what == 0) {

                route.clear();
                Destination[] list;
                list = (Destination[]) m.obj;

                for (Destination destination : list) {
                    if (destination != null) {
                        route.add(destination);
                    } else {
                        break;
                    }
                }

                //길을 지도위에 그려준다
                drawLine(mapView,route);

                /*
                //중간지점이 제대로 설정되었는지 로그찍는 코드
                for(int i=0;i<route.size();i++){
                    Log.i("route: ", Double.toString(route.get(i).getLongitude()) +" : "+ Double.toString(route.get(i).getLatitude()));
                }
                */
                 //리스트뷰로 보여주는 코드
                /*
                ArrayList<String> name = new ArrayList<>();
                for(int i=0;i<route.size();i++){
                        name.add("[" + route.get(i).getLatitude() + "," + route.get(i).getLongitude() + "]" );
                }

                adapter2 = new ArrayAdapter<>
                        (getApplicationContext(),android.R.layout.simple_list_item_1,name);
                listView2.setAdapter(adapter2);
                Toast.makeText(MainActivity.this, "done" + route.size(), Toast.LENGTH_LONG).show();
                */
            }
        }
    };


}