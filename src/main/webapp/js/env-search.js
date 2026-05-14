(function () {
  var searchBtn = document.getElementById('searchBtn');
  var spinner = document.getElementById('spinner');

  searchBtn.addEventListener('click', function () {
    var envKey = document.getElementById('envKey').value.trim();
    var envValue = document.getElementById('envValue').value.trim();
    var maxBuilds = document.getElementById('maxBuilds').value;
    var maxResults = document.getElementById('maxResults').value;

    if (!envKey || !envValue) {
      showError('Please enter both Key and Value.');
      return;
    }

    hideAll();
    spinner.style.display = 'inline';
    searchBtn.disabled = true;

    var url = 'search?envKey=' + encodeURIComponent(envKey)
      + '&envValue=' + encodeURIComponent(envValue)
      + '&maxBuilds=' + encodeURIComponent(maxBuilds)
      + '&maxResults=' + encodeURIComponent(maxResults);

    fetch(url, {
      method: 'GET',
      headers: { 'Accept': 'application/json' }
    })
      .then(function (response) {
        if (!response.ok) throw new Error('HTTP ' + response.status);
        return response.json();
      })
      .then(function (data) {
        spinner.style.display = 'none';
        searchBtn.disabled = false;

        if (data.results && data.results.length > 0) {
          showResults(data);
        } else {
          document.getElementById('noResults').style.display = 'block';
        }
      })
      .catch(function (err) {
        spinner.style.display = 'none';
        searchBtn.disabled = false;
        showError('Search failed: ' + err.message);
      });
  });

  document.getElementById('envKey').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') searchBtn.click();
  });
  document.getElementById('envValue').addEventListener('keydown', function (e) {
    if (e.key === 'Enter') searchBtn.click();
  });

  function hideAll() {
    document.getElementById('resultArea').style.display = 'none';
    document.getElementById('noResults').style.display = 'none';
    document.getElementById('errorArea').style.display = 'none';
  }

  function showError(msg) {
    hideAll();
    document.getElementById('errorMsg').textContent = msg;
    document.getElementById('errorArea').style.display = 'block';
  }

  function showResults(data) {
    var tbody = document.getElementById('resultBody');
    tbody.innerHTML = '';
    document.getElementById('resultCount').textContent = data.totalFound;

    var rootUrlMeta = document.head.querySelector('[name=rootURL]')
      || document.head.querySelector('[name=ROOT_URL]');
    var base = rootUrlMeta ? rootUrlMeta.content : '';

    for (var i = 0; i < data.results.length; i++) {
      var r = data.results[i];
      var tr = document.createElement('tr');
      var cellStyle = 'padding:8px; text-align:left;';

      var tdJob = document.createElement('td');
      tdJob.setAttribute('style', cellStyle);
      tdJob.textContent = r.jobName;
      tr.appendChild(tdJob);

      var tdBuild = document.createElement('td');
      tdBuild.setAttribute('style', cellStyle);
      var a = document.createElement('a');
      a.href = base + '/' + r.buildUrl;
      a.textContent = '#' + r.buildNumber;
      tdBuild.appendChild(a);
      tr.appendChild(tdBuild);

      var tdStatus = document.createElement('td');
      tdStatus.setAttribute('style', cellStyle);
      var statusSpan = document.createElement('span');
      statusSpan.textContent = r.result;
      statusSpan.style.fontWeight = 'bold';
      if (r.result === 'SUCCESS') statusSpan.style.color = 'green';
      else if (r.result === 'FAILURE') statusSpan.style.color = 'red';
      else if (r.result === 'UNSTABLE') statusSpan.style.color = 'orange';
      else if (r.result === 'ABORTED') statusSpan.style.color = 'gray';
      else if (r.result === 'BUILDING') statusSpan.style.color = 'blue';
      tdStatus.appendChild(statusSpan);
      tr.appendChild(tdStatus);

      var tdTime = document.createElement('td');
      tdTime.setAttribute('style', cellStyle);
      tdTime.textContent = r.timestamp ? new Date(r.timestamp).toLocaleString() : '-';
      tr.appendChild(tdTime);

      var tdDuration = document.createElement('td');
      tdDuration.setAttribute('style', cellStyle);
      tdDuration.textContent = formatDuration(r.duration);
      tr.appendChild(tdDuration);

      tbody.appendChild(tr);
    }

    document.getElementById('resultArea').style.display = 'block';
  }

  function formatDuration(ms) {
    if (!ms || ms <= 0) return '-';
    var sec = Math.floor(ms / 1000);
    if (sec < 60) return sec + 's';
    var min = Math.floor(sec / 60);
    sec = sec % 60;
    if (min < 60) return min + 'm ' + sec + 's';
    var hr = Math.floor(min / 60);
    min = min % 60;
    return hr + 'h ' + min + 'm ' + sec + 's';
  }
})();
