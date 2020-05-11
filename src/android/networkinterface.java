package com.albahra.plugin.networkinterface;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.Proxy.Type;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Enumeration;
import java.util.logging.*;

public class networkinterface extends CordovaPlugin {
	public static final String GET__WIFI_IP_ADDRESS="getWiFiIPAddress";
	public static final String GET_CARRIER_IP_ADDRESS="getCarrierIPAddress";
	public static final String GET_HTTP_PROXY_INFORMATION="getHttpProxyInformation";
	public static final String GET_VPN_INFORMATION="getVpnInformation";
	private static final String TAG = "cordova-plugin-networkinterface";



	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		if (GET__WIFI_IP_ADDRESS.equals(action)) {
			cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					try{
						extractIpInfo(getWiFiIPAddress(), callbackContext);
					}catch(Exception e){
						callbackError(action, callbackContext, e);
					}						
				}
			});
			return true;
		} else if (GET_CARRIER_IP_ADDRESS.equals(action)) {
			cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					try{
						extractIpInfo(getCarrierIPAddress(), callbackContext);
					}catch(Exception e){
						callbackError(action, callbackContext, e);
					}
				}
			});
			return true;
		} else if(GET_HTTP_PROXY_INFORMATION.equals(action)) {
			cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					try{
						getHttpProxyInformation(args.getString(0), callbackContext);
					}catch(Exception e){
						callbackError(action, callbackContext, e);
					}
				}
			});
			return true;
		} else if(GET_VPN_INFORMATION.equals(action)) {
			cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					try{
						getVpnInformation(callbackContext);
					}catch(Exception e){
						callbackError(action, callbackContext, e);
					}
				}
			});
			return true;
		}
		else{
			cordova.getActivity().runOnUiThread(new Runnable() {
				public void run() {
					callbackContext.error("Error no such method '" + action + "'");
				}
			});
			return false;	
		}
	}

	private void callbackError(String action, CallbackContext callbackContext, Exception e){
		callbackContext.error("Error while calling ''" + action + "' '" + e.getMessage());
	}

	private JSONObject createProxyInformation (Proxy.Type proxyType, String host, String port) throws JSONException {
		JSONObject proxyInformation = new JSONObject();
		proxyInformation.put("type", proxyType.toString());
		proxyInformation.put("host", host);
		proxyInformation.put("port", port);
		return proxyInformation;
	}

	private void getHttpProxyInformation(String url, CallbackContext callbackContext) throws JSONException, URISyntaxException {
		JSONArray proxiesInformation = new JSONArray();
		ProxySelector defaultProxySelector = ProxySelector.getDefault();

		if(defaultProxySelector != null){
			List<java.net.Proxy> proxyList = defaultProxySelector.select(new URI(url));
			for(java.net.Proxy proxy: proxyList){
				if (java.net.Proxy.Type.DIRECT.equals(proxy.type())) {
                	break;
            	}
				InetSocketAddress proxyAddress = (InetSocketAddress)proxy.address();
				if(proxyAddress != null){
					proxiesInformation.put(createProxyInformation(proxy.type(), proxyAddress.getHostString(), String.valueOf(proxyAddress.getPort())));
				}
			}
		}

		if(proxiesInformation.length() < 1){
			proxiesInformation.put(createProxyInformation(Proxy.Type.DIRECT, "none", "none"));
		}

		callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, proxiesInformation));
	}

	private void extractIpInfo(String[] ipInfo, CallbackContext callbackContext) throws JSONException {
		String ip = ipInfo[0];
		String subnet = ipInfo[1];
		String fail = "0.0.0.0";
		// if (ip == null || ip.equals(fail)) {
		// 	callbackContext.error("No valid IP address identified");
		// 	return false;
		// }

		Map<String,String> ipInformation = new HashMap<String,String>();
		ipInformation.put("ip", ip);
		ipInformation.put("subnet", subnet);
		ipInformation.put("WifiApIp", getWifiApIpAddress());

		callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, new JSONObject(ipInformation)));
	}

	private String[] getWiFiIPAddress() {
		WifiManager wifiManager = (WifiManager) cordova.getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		WifiInfo wifiInfo = wifiManager.getConnectionInfo();
		int ip = wifiInfo.getIpAddress();

		String ipString = String.format(
			"%d.%d.%d.%d",
			(ip & 0xff),
			(ip >> 8 & 0xff),
			(ip >> 16 & 0xff),
			(ip >> 24 & 0xff)
		);

		String subnet = "";
		try {
			InetAddress inetAddress = InetAddress.getByName(ipString);
			subnet = getIPv4Subnet(inetAddress);
		} catch (Exception e) {
		}

		return new String[]{ ipString, subnet };
	}

	private String getWifiApIpAddress() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				if (intf.getName().contains("wlan")) {
					for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
							.hasMoreElements();) {
						InetAddress inetAddress = enumIpAddr.nextElement();
						if (!inetAddress.isLoopbackAddress()
								&& (inetAddress.getAddress().length == 4)) {
							Log.d(TAG, inetAddress.getHostAddress());
							return inetAddress.getHostAddress();
						}
					}
				}
			}
		} catch (SocketException ex) {
			Log.e(TAG, ex.toString());
		}
		return "";
	}

	private String[] getCarrierIPAddress() {
	  try {
	    for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
	       NetworkInterface intf = (NetworkInterface) en.nextElement();
	       //Log.e(TAG, "Interface: " + intf.toString() + " name: " + intf.getName() + " display nane: " + intf.getDisplayName() );
	       for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
	          InetAddress inetAddress = enumIpAddr.nextElement();
			   if (!inetAddress.isLoopbackAddress() && (!intf.getName().equals("wlan0")) && inetAddress instanceof Inet4Address) {
				   String ipaddress = inetAddress.getHostAddress().toString();
				   String subnet = getIPv4Subnet(inetAddress);
				   return new String[]{ ipaddress, subnet };
	          }
	       }
	    }
	  } catch (SocketException ex) {
	     Log.e(TAG, "Exception in Get IP Address: " + ex.toString());
	  }
	  return new String[]{ null, null };
	}

	private void getVpnInformation(CallbackContext callbackContext) {
		boolean enabled = isVpnEnable();
		callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, enabled));
	}

	public static boolean isVpnEnable() {
        try {
            Enumeration<NetworkInterface> list =  NetworkInterface.getNetworkInterfaces();
            while (list.hasMoreElements()) {
                NetworkInterface networkInterface = list.nextElement();
                if (networkInterface.isUp()) {
                    String interfaceName = networkInterface.getName();
                    if (interfaceName.contains("tun") || interfaceName.contains("ppp") || interfaceName.contains("pptp")) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {}
        return false;
    }

	public static String getIPv4Subnet(InetAddress inetAddress) {
		try {
			NetworkInterface ni = NetworkInterface.getByInetAddress(inetAddress);
			List<InterfaceAddress> intAddrs =  ni.getInterfaceAddresses();
			for (InterfaceAddress ia : intAddrs) {
				if (!ia.getAddress().isLoopbackAddress() && ia.getAddress() instanceof Inet4Address) {
					return getIPv4SubnetFromNetPrefixLength(ia.getNetworkPrefixLength()).getHostAddress().toString();
				}
			}
		} catch (Exception e) {
		}
		return "";
	}

	public static InetAddress getIPv4SubnetFromNetPrefixLength(int netPrefixLength) {
		try {
			int shift = (1<<31);
			for (int i=netPrefixLength-1; i>0; i--) {
				shift = (shift >> 1);
			}
			String subnet = Integer.toString((shift >> 24) & 255) + "." + Integer.toString((shift >> 16) & 255) + "." + Integer.toString((shift >> 8) & 255) + "." + Integer.toString(shift & 255);
			return InetAddress.getByName(subnet);
		}
		catch(Exception e){
		}
		return null;
	}
}
