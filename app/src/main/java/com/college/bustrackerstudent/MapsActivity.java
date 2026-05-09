package com.college.bustrackerstudent;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private Marker busMarker;
    private Marker stopMarker;

    private DatabaseReference selectedBusLocationRef;
    private DatabaseReference allBusLocationRef;
    private DatabaseReference studentRef;
    private ValueEventListener selectedBusLocationListener;

    private String selectedBusId = "";
    private String studentId = "";
    private String studentName = "Unknown Student";
    private String stopName = "My Stop";

    private TextView tvMissedBus;
    private TextView tvBusSpeed;
    private TextView tvETA;
    private Button btnNearbyBuses;
    private ListView listNearbyBuses;
    private SpeedMeterView speedMeterView;

    private double studentStopLat = 16.5062;
    private double studentStopLng = 80.6480;

    private boolean busReachedStop = false;
    private boolean busMissed = false;
    private boolean nearStopNotificationSent = false;

    private final ArrayList<String> nearbyBusDisplayList = new ArrayList<>();
    private final ArrayList<NearbyBusModel> nearbyBusModels = new ArrayList<>();
    private ArrayAdapter<String> nearbyAdapter;

    private static final String BUS_ALERT_CHANNEL_ID = "bus_alert_channel";
    private static final int BUS_NEAR_NOTIFICATION_ID = 1001;
    private static final int BUS_MISSED_NOTIFICATION_ID = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        tvMissedBus = findViewById(R.id.tvMissedBus);
        tvBusSpeed = findViewById(R.id.tvBusSpeed);
        tvETA = findViewById(R.id.tvETA);
        btnNearbyBuses = findViewById(R.id.btnNearbyBuses);
        listNearbyBuses = findViewById(R.id.listNearbyBuses);
        speedMeterView = findViewById(R.id.speedMeterView);

        tvMissedBus.setVisibility(View.GONE);
        btnNearbyBuses.setVisibility(View.GONE);
        listNearbyBuses.setVisibility(View.GONE);

        if (tvBusSpeed != null) {
            tvBusSpeed.setText("0 km/h");
        }

        if (tvETA != null) {
            tvETA.setText("ETA: calculating...");
        }

        if (speedMeterView != null) {
            speedMeterView.setSpeed(0f);
        }

        createNotificationChannel();

        nearbyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, nearbyBusDisplayList);
        listNearbyBuses.setAdapter(nearbyAdapter);

        selectedBusId = normalizeBusId(getIntent().getStringExtra("selectedBusId"));

        if (selectedBusId == null || selectedBusId.trim().isEmpty()) {
            Toast.makeText(this, "Selected bus not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        studentId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "student_demo";

        // FIXED: use buses instead of busLocation
        selectedBusLocationRef = FirebaseDatabase.getInstance()
                .getReference("buses")
                .child(selectedBusId);

        // FIXED: use buses instead of busLocation
        allBusLocationRef = FirebaseDatabase.getInstance()
                .getReference("buses");

        studentRef = FirebaseDatabase.getInstance()
                .getReference("students")
                .child(studentId);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Toast.makeText(this, "Map fragment not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnNearbyBuses.setOnClickListener(v -> loadNearbyBuses());

        listNearbyBuses.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < nearbyBusModels.size()) {
                NearbyBusModel selectedNearbyBus = nearbyBusModels.get(position);

                Toast.makeText(
                        MapsActivity.this,
                        "Tracking " + selectedNearbyBus.getBusName(),
                        Toast.LENGTH_SHORT
                ).show();

                Intent intent = new Intent(MapsActivity.this, MapsActivity.class);
                intent.putExtra("selectedBusId", selectedNearbyBus.getBusId());
                startActivity(intent);
                finish();
            }
        });
    }

    private String normalizeBusId(String rawBusId) {
        if (rawBusId == null || rawBusId.trim().isEmpty()) return "";
        String trimmed = rawBusId.trim().toUpperCase(Locale.getDefault());
        String numberOnly = trimmed.replaceAll("[^0-9]", "");
        if (!numberOnly.isEmpty()) {
            if (numberOnly.length() == 1) {
                numberOnly = "0" + numberOnly;
            }
            return "BUS-" + numberOnly;
        }
        return trimmed;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        loadStudentNameAndStop();

        mMap.setOnMapClickListener(latLng -> {
            studentStopLat = latLng.latitude;
            studentStopLng = latLng.longitude;
            stopName = getStopNameFromLatLng(studentStopLat, studentStopLng);

            if (stopMarker != null) {
                stopMarker.remove();
            }

            stopMarker = mMap.addMarker(
                    new MarkerOptions()
                            .position(latLng)
                            .title(stopName)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            );

            saveStudentStop();

            busReachedStop = false;
            busMissed = false;
            nearStopNotificationSent = false;

            tvMissedBus.setVisibility(View.GONE);
            btnNearbyBuses.setVisibility(View.GONE);
            listNearbyBuses.setVisibility(View.GONE);

            if (tvETA != null) {
                tvETA.setText("ETA: calculating...");
            }

            Toast.makeText(this, "Stop saved: " + stopName, Toast.LENGTH_SHORT).show();
        });

        startLiveTracking();
    }

    private void loadStudentNameAndStop() {
        studentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                if (snapshot.exists()) {
                    studentName = getStringValue(snapshot.child("name"), "Unknown Student");

                    String savedBus = getStringValue(snapshot.child("selectedBusId"), selectedBusId);
                    if (savedBus.isEmpty()) {
                        savedBus = getStringValue(snapshot.child("selectedBus"), selectedBusId);
                    }
                    if (!savedBus.isEmpty()) {
                        selectedBusId = normalizeBusId(savedBus);
                    }

                    stopName = getStringValue(snapshot.child("stopName"), "My Stop");

                    double savedLat = safeGetDouble(snapshot.child("stopLat"));
                    double savedLng = safeGetDouble(snapshot.child("stopLng"));

                    if (!(savedLat == 0.0 && savedLng == 0.0)) {
                        studentStopLat = savedLat;
                        studentStopLng = savedLng;
                    }
                }

                LatLng stop = new LatLng(studentStopLat, studentStopLng);

                if (stopMarker != null) {
                    stopMarker.remove();
                }

                stopMarker = mMap.addMarker(
                        new MarkerOptions()
                                .position(stop)
                                .title(stopName)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                );

                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(stop, 14f));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                LatLng stop = new LatLng(studentStopLat, studentStopLng);

                if (stopMarker != null) {
                    stopMarker.remove();
                }

                stopMarker = mMap.addMarker(
                        new MarkerOptions()
                                .position(stop)
                                .title(stopName)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                );

                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(stop, 14f));
            }
        });
    }

    private void saveStudentStop() {
        studentRef.child("studentName").setValue(studentName);
        studentRef.child("selectedBus").setValue(selectedBusId);
        studentRef.child("selectedBusId").setValue(selectedBusId);
        studentRef.child("stopName").setValue(stopName);
        studentRef.child("stopLat").setValue(studentStopLat);
        studentRef.child("stopLng").setValue(studentStopLng);
        studentRef.child("uid").setValue(studentId);
    }

    private String getStopNameFromLatLng(double lat, double lng) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<android.location.Address> addresses = geocoder.getFromLocation(lat, lng, 1);

            if (addresses != null && !addresses.isEmpty()) {
                android.location.Address address = addresses.get(0);

                if (address.getFeatureName() != null && !address.getFeatureName().isEmpty()) {
                    return address.getFeatureName();
                } else if (address.getSubLocality() != null && !address.getSubLocality().isEmpty()) {
                    return address.getSubLocality();
                } else if (address.getLocality() != null && !address.getLocality().isEmpty()) {
                    return address.getLocality();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "My Stop";
    }

    private void startLiveTracking() {
        selectedBusLocationListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(MapsActivity.this, "Live bus location not found", Toast.LENGTH_SHORT).show();
                    return;
                }

                double lat = safeGetDouble(snapshot.child("latitude"));
                double lng = safeGetDouble(snapshot.child("longitude"));
                double speed = safeGetDouble(snapshot.child("speed"));

                String busName = snapshot.child("busName").getValue(String.class);
                String status = snapshot.child("status").getValue(String.class);

                String finalBusName = (busName != null && !busName.isEmpty()) ? busName : selectedBusId;
                String finalStatus = (status != null && !status.isEmpty()) ? status : "unknown";

                if (lat == 0.0 && lng == 0.0) {
                    Toast.makeText(MapsActivity.this, "Location data invalid for this bus", Toast.LENGTH_SHORT).show();
                    return;
                }

                LatLng busLatLng = new LatLng(lat, lng);

                if (busMarker == null) {
                    busMarker = mMap.addMarker(
                            new MarkerOptions()
                                    .position(busLatLng)
                                    .title(finalBusName + " (" + finalStatus + ")")
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    );
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(busLatLng, 16f));
                } else {
                    busMarker.setPosition(busLatLng);
                    busMarker.setTitle(finalBusName + " (" + finalStatus + ")");
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(busLatLng));
                }

                if (tvBusSpeed != null) {
                    tvBusSpeed.setText(Math.round(speed) + " km/h");
                }

                if (speedMeterView != null) {
                    speedMeterView.setSpeed((float) speed);
                }

                float[] results = new float[1];
                Location.distanceBetween(
                        lat,
                        lng,
                        studentStopLat,
                        studentStopLng,
                        results
                );

                float distanceMeters = results[0];
                double distanceKm = distanceMeters / 1000.0;

                if (speed > 5) {
                    double etaHours = distanceKm / speed;
                    int etaMinutes = (int) Math.ceil(etaHours * 60);

                    if (tvETA != null) {
                        if (etaMinutes <= 0) {
                            tvETA.setText("ETA: arriving");
                        } else {
                            tvETA.setText("ETA: " + etaMinutes + " min");
                        }
                    }
                } else {
                    if (tvETA != null) {
                        tvETA.setText("ETA: calculating...");
                    }
                }

                if (distanceMeters < 500 && !nearStopNotificationSent) {
                    showBusNearNotification();
                    nearStopNotificationSent = true;
                }

                if (distanceMeters > 600) {
                    nearStopNotificationSent = false;
                }

                checkIfBusMissed(lat, lng);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MapsActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        // FIXED: use buses instead of busLocation
        selectedBusLocationRef = FirebaseDatabase.getInstance()
                .getReference("buses")
                .child(selectedBusId);

        selectedBusLocationRef.addValueEventListener(selectedBusLocationListener);
    }

    private void checkIfBusMissed(double busLat, double busLng) {
        float[] results = new float[1];

        Location.distanceBetween(
                busLat,
                busLng,
                studentStopLat,
                studentStopLng,
                results
        );

        float distanceToStop = results[0];

        if (!busReachedStop && distanceToStop <= 200) {
            busReachedStop = true;
        }

        if (busReachedStop && distanceToStop > 500 && !busMissed) {
            busMissed = true;

            if (tvMissedBus != null) {
                tvMissedBus.setVisibility(View.VISIBLE);
                tvMissedBus.setText("⚠ Your bus has missed");
            }

            btnNearbyBuses.setVisibility(View.VISIBLE);

            Toast.makeText(this, "Your bus has missed", Toast.LENGTH_LONG).show();

            showBusMissedNotification();
        }
    }

    private void loadNearbyBuses() {
        allBusLocationRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                nearbyBusModels.clear();
                nearbyBusDisplayList.clear();

                for (DataSnapshot busSnapshot : snapshot.getChildren()) {
                    String busId = busSnapshot.getKey();

                    if (busId == null) continue;
                    if (busId.equalsIgnoreCase(selectedBusId)) continue;

                    double lat = safeGetDouble(busSnapshot.child("latitude"));
                    double lng = safeGetDouble(busSnapshot.child("longitude"));

                    String busName = busSnapshot.child("busName").getValue(String.class);
                    String status = busSnapshot.child("status").getValue(String.class);

                    if (busName == null || busName.trim().isEmpty()) {
                        busName = busId;
                    }

                    if (status == null) {
                        status = "";
                    }

                    if (!status.equalsIgnoreCase("online")
                            && !status.equalsIgnoreCase("running")
                            && !status.equalsIgnoreCase("active")) {
                        continue;
                    }

                    float[] results = new float[1];
                    Location.distanceBetween(
                            studentStopLat,
                            studentStopLng,
                            lat,
                            lng,
                            results
                    );

                    float distanceMeters = results[0];

                    NearbyBusModel model = new NearbyBusModel(
                            busId,
                            busName,
                            lat,
                            lng,
                            distanceMeters
                    );

                    nearbyBusModels.add(model);
                }

                Collections.sort(nearbyBusModels, Comparator.comparingDouble(NearbyBusModel::getDistanceMeters));

                int limit = Math.min(5, nearbyBusModels.size());

                for (int i = 0; i < limit; i++) {
                    NearbyBusModel bus = nearbyBusModels.get(i);

                    String distanceText;
                    if (bus.getDistanceMeters() < 1000) {
                        distanceText = ((int) bus.getDistanceMeters()) + " m";
                    } else {
                        distanceText = String.format(Locale.getDefault(), "%.2f km", bus.getDistanceMeters() / 1000.0);
                    }

                    nearbyBusDisplayList.add(bus.getBusName() + "  -  " + distanceText);
                }

                nearbyAdapter.notifyDataSetChanged();
                listNearbyBuses.setVisibility(View.VISIBLE);

                if (nearbyBusDisplayList.isEmpty()) {
                    Toast.makeText(MapsActivity.this, "No nearby buses found", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MapsActivity.this, "Tap a nearby bus to track it", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MapsActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    BUS_ALERT_CHANNEL_ID,
                    "Bus Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications when bus is near your stop");

            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showBusNearNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, BUS_ALERT_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Bus Alert")
                .setContentText("Your bus is near your stop!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(BUS_NEAR_NOTIFICATION_ID, builder.build());
    }

    private void showBusMissedNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, BUS_ALERT_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Bus Missed")
                .setContentText("Your bus has crossed your stop.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat.from(this).notify(BUS_MISSED_NOTIFICATION_ID, builder.build());
    }

    private double safeGetDouble(DataSnapshot snapshot) {
        Object value = snapshot.getValue();
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private String getStringValue(DataSnapshot snapshot, String defaultValue) {
        String value = snapshot.getValue(String.class);
        return value != null ? value : defaultValue;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (selectedBusLocationRef != null && selectedBusLocationListener != null) {
            selectedBusLocationRef.removeEventListener(selectedBusLocationListener);
        }
    }
}