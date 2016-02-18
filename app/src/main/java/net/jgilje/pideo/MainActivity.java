package net.jgilje.pideo;

import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import java.util.HashSet;
import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;

/**
 * Created by jgilje on 16.09.15.
 */
public class MainActivity extends AppCompatActivity {
    /**
     * Log tag
     */
    private static final String TAG = "MainActivity";

    private static final String PREF_MANUAL_HOSTS = "MANUAL_HOSTS";

    /**
     * The service name on mDNS
     */
    private static final String SERVICE_TYPE = "_pideo._tcp";

    private NsdManager.DiscoveryListener discoveryListener;

    private NsdManager.ResolveListener resolveListener;

    private NsdManager nsdManager;

    private RecyclerViewAdapter adapter;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FragmentTransaction ft = getFragmentManager().beginTransaction();
                Fragment prev = getFragmentManager().findFragmentByTag("manual_entry_dialog");
                if (prev != null) {
                    ft.remove(prev);
                }
                ft.addToBackStack(null);

                // Create and show the dialog.
                DialogFragment newFragment = ManualEntryFragment.newInstance(new ManualEntryFragment.Listener() {
                    @Override
                    public void onManualEntry(String host, int port) {
                        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);

                        HashSet<String> hosts = new HashSet<>(preferences.getStringSet(PREF_MANUAL_HOSTS, new HashSet<String>()));
                        hosts.add(String.format("%s:%d", host, port));

                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putStringSet(PREF_MANUAL_HOSTS, hosts);
                        editor.apply();

                        adapter.addEntry(host, port);
                    }
                });
                newFragment.show(ft, "manual_entry_dialog");
            }
        });

        RecyclerView rv = (RecyclerView) findViewById(R.id.recyclerview);
        setupRecyclerView(rv);

        nsdManager = (NsdManager) getApplicationContext().getSystemService(Context.NSD_SERVICE);
    }

    @Override
    protected void onPause() {
        super.onPause();

        nsdManager.stopServiceDiscovery(discoveryListener);
    }

    @Override
    protected void onResume() {
        super.onResume();

        populateManualEntries();
        initializeDiscoveryListener();
    }

    private void populateManualEntries() {
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        SortedSet<String> hosts = new TreeSet<>(preferences.getStringSet(PREF_MANUAL_HOSTS, new HashSet<String>()));
        for (Iterator<String> i = hosts.iterator(); i.hasNext(); ) {
            String host = i.next();
            String[] split = host.split(":");
            if (split.length != 2) {
                i.remove();
                preferences.edit().putStringSet(PREF_MANUAL_HOSTS, hosts).apply();
            } else {
                try {
                    int port = Integer.valueOf(split[1]);
                    adapter.addEntry(split[0], port);
                } catch (NumberFormatException e) {
                    preferences.edit().putStringSet(PREF_MANUAL_HOSTS, hosts).apply();
                }
            }
        }
    }

    private void setupRecyclerView(RecyclerView recyclerView) {
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        adapter = new RecyclerViewAdapter(getApplicationContext());
        recyclerView.setAdapter(adapter);
        registerForContextMenu(recyclerView);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ContextMenuRecyclerView.ContextMenuInfo info = (ContextMenuRecyclerView.ContextMenuInfo) item.getMenuInfo();

        switch (item.getItemId()) {
            case R.id.remove:
                SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);

                HashSet<String> hosts = new HashSet<>(preferences.getStringSet(PREF_MANUAL_HOSTS, new HashSet<String>()));
                hosts.remove(String.format("%s:%d", info.hostname, info.port));

                SharedPreferences.Editor editor = preferences.edit();
                editor.putStringSet(PREF_MANUAL_HOSTS, hosts);
                editor.apply();

                adapter.removeEntry(info.position);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void initializeDiscoveryListener() {
        // Instantiate a new DiscoveryListener
        final Vector<NsdServiceInfo> queue = new Vector<>();

        resolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo nsdServiceInfo, int i) {
                System.out.println("Failed resolve");
            }

            @Override
            public void onServiceResolved(NsdServiceInfo nsdServiceInfo) {
                queue.remove(0);
                if (queue.size() > 0) {
                    nsdManager.resolveService(queue.get(0), resolveListener);
                }

                final String displayName = nsdServiceInfo.getServiceName();
                final String canonicalHostName = nsdServiceInfo.getHost().getCanonicalHostName();
                final int port = nsdServiceInfo.getPort();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.addEntry(displayName, canonicalHostName, port);
                    }
                });
            }
        };

        discoveryListener = new NsdManager.DiscoveryListener() {
            //  Called as soon as service discovery begins.
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo nsdServiceInfo) {
                queue.add(nsdServiceInfo);
                if (queue.size() == 1) {
                    nsdManager.resolveService(nsdServiceInfo, resolveListener);
                }
            }

            @Override
            public void onServiceLost(final NsdServiceInfo service) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                Log.e(TAG, "service lost" + service);

                if (service.getHost() == null) {
                    return;
                }

                final String canonicalHostName = service.getHost().getCanonicalHostName();
                final int port = service.getPort();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.removeEntry(canonicalHostName, port, false);
                    }
                });
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "Discovery stopped: " + serviceType);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.clearEntries();
                    }
                });
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }
        };

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }
}
