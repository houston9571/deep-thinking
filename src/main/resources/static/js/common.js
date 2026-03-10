

function judgeColor(value){
	if(!isEmpty(value) && typeof value === 'string')
		return value.startsWith('-') ? 'color: green' : 'color: red';
	return "";
}

function isEmpty(value) {
	return (
		value == null ||                      // 检查 null 或 undefined
		(typeof value === 'string' && value.trim() === '') || // 空字符串或仅空白
		(Array.isArray(value) && value.length === 0) ||       // 空数组
		(typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length === 0) // 空对象
	);
}

var sortable;
function saveSortData(id, url) {
	var el = document.getElementById(id);
	sortable = Sortable.create(el, { 
		onEnd: function(evt) {
			//evt.oldIndex; // element's old index within old parent
			//evt.newIndex; // element's new index within new parent
			var new_order = [];
			evt.to.childNodes.forEach(function(tr) {
				if (tr.id)
					new_order.push(tr.id)
			});
			axios.post(url, {
					newOrder: new_order.join(',')
				})
				.catch(function(error) {
					alert("系统异常");
				});
		}
	});
}
function destroySort() {
	sortable.destroy();
}

function moveTop(trId, tbId, url) {
	var tr = document.getElementById(trId);
	var tb = document.getElementById(tbId);
	tb.deleteRow(tr.rowIndex - 1);
	tb.insertBefore(tr, tb.childNodes.item(1));
	var new_order = [];
	tb.childNodes.forEach(function(e) {
		if (e.id)
			new_order.push(e.id)
	});
	axios.post(url, {
			newOrder: new_order.join(',')
		})
		.catch(function(error) {
			alert("系统异常");
		});
}

function wwdoSort(event, tableData) {
	if (this.isDragging) {
		destroySort();
		this.isDragging = false;
	}
	// console.log(event.target.className)
	// console.log(event.target.id)
	var className = event.target.className;
	className = className == '' || className == 'asc' ? 'desc' : 'asc';
	document.querySelectorAll('.desc').forEach(a => {
		a.className = ''
	})
	document.querySelectorAll('.asc').forEach(a => {
		a.className = ''
	})
	event.target.className = className;
	let data = tableData.slice(0);
	var id = event.target.id;
	var hasSub = id.indexOf(".") > 0;
	if (hasSub) {
		var i = id.indexOf(".");
		id_o = id.substr(0, i);
		id_t = id.substr(i + 1);
	}
	if (className === "asc") {
		data.sort((a, b) => {
			if (hasSub) {
				if (a[id_o] == null || a[id_o][id_t] == null || a[id_o][id_t] == '')
					return 1;
				if (b[id_o] == null || b[id_o][id_t] == null || b[id_o][id_t] == '')
					return -1;
				re = a[id_o][id_t] - b[id_o][id_t];
				return isNaN(re) ? a[id_o][id_t].localeCompare(b[id_o][id_t]) : re;
			} else {
				if (a[id] == null || a[id] == '')
					return 1;
				if (b[id] == null || b[id] == '')
					return -1;
				re = a[id] - b[id];
				return isNaN(re) ? a[id].localeCompare(b[id]) : re;
			}
		});
	} else if (className === "desc") {
		data.sort((a, b) => {
			if (hasSub) {
				if (a[id_o] == null || a[id_o][id_t] == null || a[id_o][id_t] == '')
					return 1;
				if (b[id_o] == null || b[id_o][id_t] == null || b[id_o][id_t] == '')
					return -1;
				re = b[id_o][id_t] - a[id_o][id_t];
				return isNaN(re) ? b[id_o][id_t].localeCompare(a[id_o][id_t]) : re;
			} else {
				if (a[id] == null || a[id] == '')
					return 1;
				if (b[id] == null || b[id] == '')
					return -1;
				re = b[id] - a[id];
				return isNaN(re) ? b[id].localeCompare(a[id]) : re;
			}
		});
	}
 	return  data;
}