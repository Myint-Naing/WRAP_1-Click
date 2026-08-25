'use strict';
'use ui';

var fs = L.require('fs');
var ui = L.require('ui');
var network = L.require('network');

return L.view.extend({
	load: function() {
		return Promise.all([
			L.require('fs'),
			L.require('ui'),
			L.require('network')
		]);
	},

	handleAction: function(action) {
		var self = this;

		ui.showModal(_('Executing...'), [
			E('p', { 'class': 'spinning' }, _('Processing request, please wait...'))
		]);

		return fs.exec('/usr/bin/warp-script', [action])
			.then(function(res) {
				ui.hideModal();
				if (res.code !== 0) {
					var errText = (res.stderr && res.stderr.trim()) ? res.stderr.trim() : _('Execution failed with code ') + res.code;
					ui.addNotification(null, E('p', _('Action failed: ') + errText));
				} else {
					location.reload();
				}
			})
			.catch(function(err) {
				ui.hideModal();
				ui.addNotification(null, E('p', _('Action failed: ') + (err.message || err)));
			});
	},

	renderStatus: function(statusData, netData) {
		var isConnected = false;
		if (netData && typeof netData.isUp === 'function' && netData.isUp()) {
			isConnected = true;
		} else if (statusData && statusData.enabled === '1' && statusData.status === 'connected') {
			isConnected = true;
		}

		var statusBadge = isConnected ?
			E('span', { 'class': 'label success' }, _('Connected')) :
			E('span', { 'class': 'label danger' }, _('Disconnected'));

		var timeRemainingText = _('N/A');
		if (statusData && statusData.time_remaining_seconds !== undefined && statusData.time_remaining_seconds !== null) {
			var totalSec = parseInt(statusData.time_remaining_seconds, 10);
			if (!isNaN(totalSec) && totalSec > 0) {
				var days = Math.floor(totalSec / 86400);
				var hours = Math.floor((totalSec % 86400) / 3600);
				var mins = Math.floor((totalSec % 3600) / 60);
				timeRemainingText = days + 'd ' + hours + 'h ' + mins + 'm';
			} else if (statusData.enabled === '1') {
				timeRemainingText = _('Expired / Renewal Pending');
			}
		}

		return E('div', { 'class': 'cbi-section' }, [
			E('h3', _('Cloudflare WARP Status')),
			E('table', { 'class': 'table' }, [
				E('tr', { 'class': 'tr' }, [
					E('td', { 'class': 'td left', 'width': '33%' }, _('Status')),
					E('td', { 'class': 'td left' }, statusBadge)
				]),
				E('tr', { 'class': 'tr' }, [
					E('td', { 'class': 'td left' }, _('IPv4 Address')),
					E('td', { 'class': 'td left' }, (statusData && statusData.ipv4) ? statusData.ipv4 : '-')
				]),
				E('tr', { 'class': 'tr' }, [
					E('td', { 'class': 'td left' }, _('IPv6 Address')),
					E('td', { 'class': 'td left' }, (statusData && statusData.ipv6) ? statusData.ipv6 : '-')
				]),
				E('tr', { 'class': 'tr' }, [
					E('td', { 'class': 'td left' }, _('Endpoint')),
					E('td', { 'class': 'td left' }, (statusData && statusData.endpoint) ? statusData.endpoint : '-')
				]),
				E('tr', { 'class': 'tr' }, [
					E('td', { 'class': 'td left' }, _('Key Expires In')),
					E('td', { 'class': 'td left' }, E('strong', timeRemainingText))
				])
			])
		]);
	},

	render: function(loadedData) {
		var self = this;

		return Promise.all([
			fs.exec('/usr/bin/warp-script', ['status']).then(function(res) {
				if (res && res.code === 0 && res.stdout) {
					try {
						return JSON.parse(res.stdout);
					} catch(e) {
						return {};
					}
				}
				return {};
			}).catch(function() { return {}; }),
			L.resolveDefault(network.getDevice('warp'), null),
			L.resolveDefault(network.getNetwork('warp'), null)
		]).then(function(results) {
			var statusData = results[0] || {};
			var netDev = results[1];
			var netIface = results[2];

			var isUp = (netIface && typeof netIface.isUp === 'function' && netIface.isUp()) || (statusData.enabled === '1');

			var btnEnable = E('button', {
				'class': 'btn cbi-button cbi-button-action',
				'click': ui.createHandlerFn(self, function() {
					return self.handleAction('enable');
				})
			}, _('1-Click Enable WARP'));

			var btnDisable = E('button', {
				'class': 'btn cbi-button cbi-button-negative',
				'click': ui.createHandlerFn(self, function() {
					return self.handleAction('disable');
				})
			}, _('Disable WARP'));

			var btnRenew = E('button', {
				'class': 'btn cbi-button cbi-button-neutral',
				'click': ui.createHandlerFn(self, function() {
					return self.handleAction('renew');
				})
			}, _('Renew Key Now'));

			var actionButtons = isUp ? [btnDisable, ' ', btnRenew] : [btnEnable];

			return E('div', { 'class': 'cbi-map' }, [
				E('h2', _('1-Click Cloudflare WARP')),
				E('div', { 'class': 'cbi-map-descr' },
					_('Seamlessly route LAN traffic through Cloudflare WARP WireGuard tunnel with automated key renewal.')),
				self.renderStatus(statusData, netIface),
				E('div', { 'class': 'cbi-section' }, [
					E('h3', _('Actions')),
					E('div', { 'class': 'cbi-value' }, actionButtons)
				])
			]);
		});
	}
});
