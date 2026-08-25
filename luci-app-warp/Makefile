include $(TOPDIR)/rules.mk

PKG_NAME:=luci-app-warp
PKG_VERSION:=1.0.0
PKG_RELEASE:=1

PKG_MAINTAINER:=OpenWrt Developer <developer@openwrt.org>
PKG_LICENSE:=MIT

include $(INCLUDE_DIR)/package.mk

define Package/luci-app-warp
  SECTION:=luci
  CATEGORY:=LuCI
  SUBMENU:=3. Applications
  TITLE:=LuCI support for 1-Click Cloudflare WARP WireGuard integration
  DEPENDS:=+wireguard-tools +curl +jsonfilter +uci +luci-base
  PKGARCH:=all
endef

define Package/luci-app-warp/description
  Provides a simple 1-Click LuCI web interface and backend scripts for
  integrating Cloudflare WARP with WireGuard on OpenWrt, including automatic
  key generation, firewall zone binding, and 48-hour expiration auto-renewal.
endef

define Build/Compile
endef

define Package/luci-app-warp/install
	$(INSTALL_DIR) $(1)/usr/bin
	$(INSTALL_BIN) ./root/usr/bin/warp-script $(1)/usr/bin/warp-script
	$(INSTALL_BIN) ./root/usr/bin/warp-renew $(1)/usr/bin/warp-renew

	$(INSTALL_DIR) $(1)/etc/init.d
	$(INSTALL_BIN) ./root/etc/init.d/warp $(1)/etc/init.d/warp

	$(INSTALL_DIR) $(1)/etc/config
	$(INSTALL_CONF) ./root/etc/config/warp $(1)/etc/config/warp

	$(INSTALL_DIR) $(1)/usr/share/luci/menu.d
	$(INSTALL_DATA) ./root/usr/share/luci/menu.d/luci-app-warp.json $(1)/usr/share/luci/menu.d/luci-app-warp.json

	$(INSTALL_DIR) $(1)/usr/share/rpcd/acl.d
	$(INSTALL_DATA) ./root/usr/share/rpcd/acl.d/luci-app-warp.json $(1)/usr/share/rpcd/acl.d/luci-app-warp.json

	$(INSTALL_DIR) $(1)/www/luci-static/resources/view/warp
	$(INSTALL_DATA) ./htdocs/luci-static/resources/view/warp/overview.js $(1)/www/luci-static/resources/view/warp/overview.js
endef

define Package/luci-app-warp/postinst
#!/bin/sh
if [ -z "$${IPKG_INSTROOT}" ]; then
	/etc/init.d/warp enable >/dev/null 2>&1 || true
	/etc/init.d/warp start >/dev/null 2>&1 || true
fi
exit 0
endef

$(eval $(call BuildPackage,luci-app-warp))
