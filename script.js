const API = "/api";
let state = { courses: [] };
let lastSearchId = null;

const $ = id => document.getElementById(id);

async function api(path, options = {}) {
  const response = await fetch(API + path, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.error || "Request failed.");
  }

  return data;
}

function escapeHTML(value) {
  return String(value).replace(/[&<>"']/g, char => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;"
  })[char]);
}

function setMessage(id, message, type = "success") {
  const el = $(id);
  el.textContent = message;
  el.className = "message " + type;
}

function clearMessage(id) {
  const el = $(id);
  el.textContent = "";
  el.className = "message";
}

function getCourse(id) {
  return state.courses.find(c => c.id === String(id));
}

function studentName(id) {
  for (const c of state.courses) {
    for (const s of [...c.enrolledStudents, ...c.waitingStudents]) {
      if (s.id === id) return s.name;
    }
  }
  return id;
}

// Load course data from the Java backend
async function refresh() {
  try {
    state = await api("/courses");
    renderAll();
  } catch (e) {
    setMessage(
      "registrationMessage",
      "Cannot connect to Java backend. Start CourseFlow using the instructions in README.txt.",
      "error"
    );
  }
}

function updateSummary() {
  $("totalCourses").textContent =
    state.totalCourses ?? state.courses.length;
  $("totalEnrolled").textContent = state.totalEnrolled ?? 0;
  $("totalSeats").textContent = state.totalSeats ?? 0;
  $("totalWaiting").textContent = state.totalWaiting ?? 0;
}

function renderCourseOptions() {
  const prevReg = $("registerCourse").value;
  const prevRoster = $("rosterCourse").value;

  const options = state.courses.map(c =>
    `<option value="${escapeHTML(c.id)}">${escapeHTML(c.id)} — ${escapeHTML(c.name)}</option>`
  ).join("");

  $("registerCourse").innerHTML =
    options || '<option value="">No courses available</option>';

  $("rosterCourse").innerHTML =
    options || '<option value="">No courses available</option>';

  if (getCourse(prevReg)) $("registerCourse").value = prevReg;
  if (getCourse(prevRoster)) $("rosterCourse").value = prevRoster;
}

function renderCourses() {
  const filter = $("courseFilter").value.trim().toLowerCase();

  const courses = state.courses.filter(c =>
    c.id.toLowerCase().includes(filter) ||
    c.name.toLowerCase().includes(filter)
  );

  if (!courses.length) {
    $("courseGrid").innerHTML =
      '<div class="empty-state">No courses found.</div>';
    return;
  }

  $("courseGrid").innerHTML = courses.map(c => {
    const seats = c.capacity - c.enrolled.length;
    const full = seats <= 0;

    return `
      <article class="course-card">
        <div class="course-top">
          <span class="course-code">COURSE ${escapeHTML(c.id)}</span>
          <span class="pill ${full ? "full" : "open"}">
            ${full ? "FULL" : "OPEN"}
          </span>
        </div>

        <h4>${escapeHTML(c.name)}</h4>

        <div class="course-meta">
          <span>${c.enrolled.length}/${c.capacity} seats filled</span>
          <span>${c.waitlist.length} waiting</span>
        </div>

        <div class="course-actions">
          <button class="secondary"
            data-register-course="${escapeHTML(c.id)}">
            Register
          </button>

          <button class="secondary"
            data-view-course="${escapeHTML(c.id)}">
            View roster
          </button>
        </div>
      </article>
    `;
  }).join("");

  document.querySelectorAll("[data-register-course]").forEach(b => {
    b.addEventListener("click", () => {
      $("registerCourse").value = b.dataset.registerCourse;
      $("studentId").focus();
      $("registerForm").scrollIntoView({
        behavior: "smooth",
        block: "center"
      });
    });
  });

  document.querySelectorAll("[data-view-course]").forEach(b => {
    b.addEventListener("click", () => {
      $("rosterCourse").value = b.dataset.viewCourse;
      renderRoster();
      $("rosterContent").scrollIntoView({
        behavior: "smooth",
        block: "center"
      });
    });
  });
}

function renderRoster() {
  const c = getCourse($("rosterCourse").value);

  if (!c) {
    $("rosterContent").innerHTML =
      '<div class="empty-state">No courses available.</div>';
    return;
  }

  const enrolled = c.enrolledStudents.map(s => `
    <div class="student-row">
      <div class="student-info">
        <strong>${escapeHTML(s.name)}</strong>
        <span>ID: ${escapeHTML(s.id)}</span>
      </div>
      <button class="small-button"
        data-withdraw="${escapeHTML(s.id)}">
        Withdraw
      </button>
    </div>
  `).join("");

  const waiting = c.waitingStudents.map((s, i) => `
    <div class="student-row">
      <div class="student-info">
        <strong>${i + 1}. ${escapeHTML(s.name)}</strong>
        <span>ID: ${escapeHTML(s.id)} · Position ${i + 1}</span>
      </div>
      <button class="small-button"
        data-cancel-wait="${escapeHTML(s.id)}">
        Cancel
      </button>
    </div>
  `).join("");

  $("rosterContent").innerHTML = `
    <div class="roster-grid">
      <div class="roster-column">
        <div class="roster-title">
          <span>Enrolled students</span>
          <span>${c.enrolled.length}/${c.capacity}</span>
        </div>
        ${enrolled || '<div class="empty-state">No enrolled students.</div>'}
      </div>

      <div class="roster-column">
        <div class="roster-title">
          <span>FIFO waitlist</span>
          <span>${c.waitlist.length} waiting</span>
        </div>
        ${waiting || '<div class="empty-state">Waitlist is empty.</div>'}
      </div>
    </div>

    <p class="muted" style="margin-top:12px">
      Available seats: ${c.capacity - c.enrolled.length}
    </p>
  `;

  $("rosterContent").querySelectorAll("[data-withdraw]").forEach(b => {
    b.addEventListener("click", () =>
      withdraw(c.id, b.dataset.withdraw)
    );
  });

  $("rosterContent").querySelectorAll("[data-cancel-wait]").forEach(b => {
    b.addEventListener("click", () =>
      withdraw(c.id, b.dataset.cancelWait)
    );
  });
}

function renderAll() {
  updateSummary();
  renderCourseOptions();
  renderCourses();
  renderRoster();
}

// Register a student
$("registerForm").addEventListener("submit", async e => {
  e.preventDefault();
  clearMessage("registrationMessage");

  try {
    const data = await api("/register", {
      method: "POST",
      body: JSON.stringify({
        courseId: $("registerCourse").value,
        id: $("studentId").value.trim(),
        name: $("studentName").value.trim()
      })
    });

    state = data;
    renderAll();
    setMessage("registrationMessage", data.message);

    $("registerForm").reset();
    renderCourseOptions();
  } catch (err) {
    setMessage("registrationMessage", err.message, "error");
  }
});

// Withdraw a student or cancel their waitlist entry
async function withdraw(courseId, id) {
  try {
    const data = await api("/withdraw", {
      method: "POST",
      body: JSON.stringify({ courseId, id })
    });

    state = data;
    renderAll();
    setMessage("registrationMessage", data.message);
  } catch (err) {
    setMessage("registrationMessage", err.message, "error");
  }
}

// Search for a student
$("searchForm").addEventListener("submit", async e => {
  e.preventDefault();

  const id = $("searchId").value.trim();
  lastSearchId = id;

  try {
    const data = await api(
      "/student?id=" + encodeURIComponent(id)
    );

    $("updateForm").classList.add("hidden");

    if (!data.student || !data.registrations.length) {
      $("searchResults").innerHTML =
        '<p class="muted">No student record found.</p>';
      return;
    }

    $("searchResults").innerHTML = `
      <div class="result-item">
        <strong>
          ${escapeHTML(data.student.name)}
          (${escapeHTML(data.student.id)})
        </strong>
        <span class="muted">
          Registered courses: ${data.registrations.length}
        </span>
      </div>
    ` + data.registrations.map(r => `
      <div class="result-item">
        <strong>${escapeHTML(r.courseName)}</strong>
        <span class="pill ${r.status === "Enrolled" ? "open" : "full"}">
          ${escapeHTML(r.status)}
        </span>
        <span class="muted">
          · Course ID ${escapeHTML(r.courseId)}
        </span>
      </div>
    `).join("");

    $("updatedName").value = data.student.name;
    $("updateForm").classList.remove("hidden");
  } catch (err) {
    $("searchResults").innerHTML =
      `<p class="message error">${escapeHTML(err.message)}</p>`;
  }
});

// Update a student's name
$("updateForm").addEventListener("submit", async e => {
  e.preventDefault();

  try {
    const data = await api("/student/update", {
      method: "POST",
      body: JSON.stringify({
        id: lastSearchId,
        name: $("updatedName").value.trim()
      })
    });

    state = data;
    renderAll();
    setMessage("registrationMessage", data.message);

    $("searchForm").requestSubmit();
  } catch (err) {
    setMessage("registrationMessage", err.message, "error");
  }
});

// Add a new course
$("addCourseForm").addEventListener("submit", async e => {
  e.preventDefault();
  clearMessage("courseMessage");

  try {
    state = await api("/courses", {
      method: "POST",
      body: JSON.stringify({
        id: $("newCourseId").value.trim(),
        name: $("newCourseName").value.trim(),
        capacity: Number($("newCourseCapacity").value)
      })
    });

    renderAll();
    $("addCourseForm").reset();
    setMessage("courseMessage", "Course added successfully.");
  } catch (err) {
    setMessage("courseMessage", err.message, "error");
  }
});

// Search and roster controls
$("courseFilter").addEventListener("input", renderCourses);
$("rosterCourse").addEventListener("change", renderRoster);

// Show and hide the add-course panel
$("showAddCourseBtn").addEventListener("click", () => {
  $("addCoursePanel").classList.remove("hidden");
  $("addCoursePanel").scrollIntoView({
    behavior: "smooth",
    block: "start"
  });
});

$("hideAddCourseBtn").addEventListener("click", () =>
  $("addCoursePanel").classList.add("hidden")
);

// Reset demo data
$("resetDemoBtn").addEventListener("click", () =>
  $("confirmDialog").showModal()
);

$("confirmDialog").addEventListener("close", async () => {
  if ($("confirmDialog").returnValue !== "confirm") return;

  try {
    state = await api("/reset", {
      method: "POST",
      body: "{}"
    });

    lastSearchId = null;
    renderAll();

    $("searchResults").innerHTML =
      '<p class="muted">Search results will appear here.</p>';

    $("updateForm").classList.add("hidden");
    clearMessage("registrationMessage");
    clearMessage("courseMessage");
  } catch (err) {
    setMessage("registrationMessage", err.message, "error");
  }
});

// Start by loading data from Java
refresh();
