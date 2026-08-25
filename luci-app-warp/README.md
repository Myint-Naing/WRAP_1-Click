# luci-app-warp: 1-Click Cloudflare WARP for OpenWrt LuCI

`luci-app-warp` is an OpenWrt LuCI application and backend integration that provides 1-Click Cloudflare WARP WireGuard setup with automated 48-hour key renewal.

---

## Features

- **1-Click Activation:** Generates a WireGuard keypair and registers directly with the Cloudflare WARP API without requiring external binaries or Go toolchains.
- **Automated UCI & Firewall Setup:** Automatically configures `/etc/config/network` and binds the WireGuard tunnel to OpenWrt's `wan` firewall zone so LAN traffic routes through WARP.
- **Automated 48-Hour Renewal:** Background cron job (`/usr/bin/warp-renew`) checks key expiration daily and automatically fetches a new key 48 hours before expiration, reloading the interface seamlessly.
- **Modern LuCI Interface:** Simple, responsive JavaScript UI (`Services -> Cloudflare WARP`) displaying status, IPv4/IPv6 addresses, endpoint, and key expiration timer.

---

## Installation Guide

### Method 1: Direct File Copy (Fastest for existing OpenWrt routers)

If you have a running OpenWrt router with SSH access, you can copy the files directly onto the router without compiling an `.ipk`.

#### Step 1: Install Required Dependencies on OpenWrt
Run the following commands on your OpenWrt router via SSH:

```bash
opkg update
opkg install wireguard-tools curl jsonfilter uci luci-base
```

#### Step 2: Copy Files to Your Router
From your local computer, copy the package root files to the router using `scp`:

```bash
# Copy backend binaries and init scripts
scp -r luci-app-warp/root/* root@192.168.1.1:/

# Copy LuCI web UI view
scp -r luci-app-warp/htdocs/* root@192.168.1.1:/www/
```

#### Step 3: Set Permissions & Enable Service
On your OpenWrt router via SSH:

```bash
chmod +x /usr/bin/warp-script /usr/bin/warp-renew /etc/init.d/warp
/etc/init.d/warp enable
/etc/init.d/warp start
/etc/init.d/rpcd restart
```

---

### Method 2: Build `.ipk` Package using OpenWrt SDK or Buildroot

#### Step 1: Place Package in OpenWrt Build Directory
Copy the `luci-app-warp` directory into your OpenWrt SDK or source tree under `package/`:

```bash
cp -r luci-app-warp /path/to/openwrt/package/luci-app-warp
```

#### Step 2: Select Package in menuconfig
Run `make menuconfig` and select `luci-app-warp`:

```text
LuCI --->
    3. Applications --->
        <*> luci-app-warp
```

#### Step 3: Compile Package
Compile the package into an `.ipk`:

```bash
make package/luci-app-warp/compile
```

The resulting `.ipk` file will be generated under `bin/packages/<architecture>/luci/luci-app-warp_1.0.0-1_all.ipk`.

#### Step 4: Install `.ipk` on Router
Transfer and install the `.ipk` on your router:

```bash
scp bin/packages/.../luci-app-warp_1.0.0-1_all.ipk root@192.168.1.1:/tmp/
ssh root@192.168.1.1 "opkg update && opkg install /tmp/luci-app-warp_1.0.0-1_all.ipk"
```

---

## How to Use

1. Open your browser and log into the **OpenWrt LuCI Web Interface**.
2. Navigate to **Services** -> **Cloudflare WARP**.
3. Click the **"1-Click Enable WARP"** button.
4. The system will generate keys, register with Cloudflare WARP, write network and firewall settings, and bring up the WireGuard interface (`warp`).
5. Status, assigned IPv4/IPv6 addresses, endpoint details, and remaining key validity will be displayed on the page.
6. Key renewal will happen automatically 48 hours prior to key expiration in the background via cron (`0 3 * * * /usr/bin/warp-renew`). You can also click **"Renew Key Now"** anytime in the UI.
