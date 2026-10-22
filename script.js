
const STORAGE_KEY = "courseflow_web_v1";

function sampleData() {
  return {
    courses: [
      { id: "101", name: "Java Programming", capacity: 2,
        enrolled: [], waitlist: [] },
      { id: "102", name: "Database Management", capacity: 3,
        enrolled: [], waitlist: [] },
      { id: "103", name: "Data Structures", capacity: 2,
        enrolled: [], waitlist: [] }
    ],
    students: {}
  };
}

function loadState() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) {
      const data = JSON.parse(saved);
      if (Array.isArray(data.courses) && data.students) {
        return data;
      }
    }
  } catch (error) {
    console.error("Could not load saved data:", error);
  }
  return sampleData();
}

let state = loadState();
let lastSearchId = null;

const $ = id => document.getElementById(id);

function saveState() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
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

function getCourse(id) {
  return state.courses.find(c => c.id === String(id));
}

function getStudentName(id) {
  return state.students[id]?.name || "Unknown student";
}

function findStudent(id) {
  return state.students[String(id).trim()] || null;
}

function setMessage(elementId, message, type = "success") {
  const element = $(elementId);
  element.textContent = message;
  element.className = "message " + type;
}

function clearMessage(elementId) {
  $(elementId).textContent = "";
  $(elementId).className = "message";
}

// Update dashboard summary
function updateSummary() {
  $("totalCourses").textContent = state.courses.length;

  $("totalEnrolled").textContent =
    state.courses.reduce((sum, c) => sum + c.enrolled.length, 0);

  $("totalSeats").textContent =
    state.courses.reduce(
      (sum, c) => sum + Math.max(0, c.capacity - c.enrolled.length), 0
    );

  $("totalWaiting").textContent =
    state.courses.reduce((sum, c) => sum + c.waitlist.length, 0);
}

// Fill course dropdowns
function renderCourseOptions() {
  const previousRegister = $("registerCourse").value;
  const previousRoster = $("rosterCourse").value;

  const options = state.courses.map(c =>
    `<option value="${escapeHTML(c.id)}">
      ${escapeHTML(c.id)} — ${escapeHTML(c.name)}
    </option>`
  ).join("");

  $("registerCourse").innerHTML =
    options || '<option value="">No courses available</option>';

  $("rosterCourse").innerHTML =
    options || '<option value="">No courses available</option>';

  if (getCourse(previousRegister)) {
    $("registerCourse").value = previousRegister;
  }

  if (getCourse(previousRoster)) {
    $("rosterCourse").value = previousRoster;
  }
}

// Display course cards
function renderCourses() {
  const filter = $("courseFilter").value.trim().toLowerCase();

  const courses = state.courses.filter(c =>
    c.id.toLowerCase().includes(filter) ||
    c.name.toLowerCase().includes(filter)
  );

  if (courses.length === 0) {
    $("courseGrid").innerHTML =
      '<div class="empty-state">No courses found.</div>';
    return;
  }

  $("courseGrid").innerHTML = courses.map(c => {
    const seats = c.capacity - c.enrolled.length;
    const full = seats === 0;

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
      </article>`;
  }).join("");

  document.querySelectorAll("[data-register-course]").forEach(button => {
    button.addEventListener("click", () => {
      $("registerCourse").value = button.dataset.registerCourse;
      $("studentId").focus();
      $("registerForm").scrollIntoView({
        behavior: "smooth", block: "center"
      });
    });
  });

  document.querySelectorAll("[data-view-course]").forEach(button => {
    button.addEventListener("click", () => {
      $("rosterCourse").value = button.dataset.viewCourse;
      renderRoster();
      $("rosterContent").scrollIntoView({
        behavior: "smooth", block: "center"
      });
    });
  });
}

// Register a student
function registerStudent(event) {
  event.preventDefault();
  clearMessage("registrationMessage");

  const course = getCourse($("registerCourse").value);
  const id = $("studentId").value.trim();
  const name = $("studentName").value.trim();

  if (!course) {
    return setMessage(
      "registrationMessage", "Please select a valid course.", "error"
    );
  }

  if (!id || !name) {
    return setMessage(
      "registrationMessage", "Enter student ID and name.", "error"
    );
  }

  if (course.enrolled.includes(id) ||
      course.waitlist.includes(id)) {
    return setMessage(
      "registrationMessage",
      "Student is already registered for this course.",
      "error"
    );
  }

  // Keep a central student record.
  if (!state.students[id]) {
    state.students[id] = { id: id, name: name };
  } else {
    state.students[id].name = name;
  }

  // Enroll if a seat is available; otherwise join FIFO queue.
  if (course.enrolled.length < course.capacity) {
    course.enrolled.push(id);

    setMessage(
      "registrationMessage",
      name + " registered successfully for " + course.name + "."
    );
  } else {
    course.waitlist.push(id);

    setMessage(
      "registrationMessage",
      course.name + " is full. " + name +
      " was added to the waitlist."
    );
  }

  saveState();
  renderAll();
  $("registerForm").reset();
}

// Withdraw an enrolled student or cancel waitlist entry
function withdrawStudent(courseId, studentId, cancelWaitlist = false) {
  const course = getCourse(courseId);

  if (!course) return;

  if (cancelWaitlist) {
    const index = course.waitlist.indexOf(studentId);

    if (index === -1) return;

    course.waitlist.splice(index, 1);
    saveState();
    renderAll();

    setMessage(
      "registrationMessage",
      getStudentName(studentId) + " was removed from the waitlist."
    );
    return;
  }

  const index = course.enrolled.indexOf(studentId);

  if (index === -1) return;

  course.enrolled.splice(index, 1);

  let message =
    getStudentName(studentId) + " was withdrawn from " + course.name + ".";

  // FIFO: promote the student who joined the queue first.
  if (course.waitlist.length > 0) {
    const nextStudentId = course.waitlist.shift();
    course.enrolled.push(nextStudentId);

    message += " " + getStudentName(nextStudentId) +
      " was promoted from the waitlist.";
  }

  saveState();
  renderAll();

  setMessage("registrationMessage", message);
}

// Show enrolled students and waiting students
function renderRoster() {
  const course = getCourse($("rosterCourse").value);

  if (!course) {
    $("rosterContent").innerHTML =
      '<div class="empty-state">No courses available.</div>';
    return;
  }

  const enrolledRows = course.enrolled.map(id => `
    <div class="student-row">
      <div class="student-info">
        <strong>${escapeHTML(getStudentName(id))}</strong>
        <span>ID: ${escapeHTML(id)}</span>
      </div>
      <button class="small-button"
        data-withdraw="${escapeHTML(id)}">Withdraw</button>
    </div>
  `).join("");

  const waitingRows = course.waitlist.map((id, index) => `
    <div class="student-row">
      <div class="student-info">
        <strong>${index + 1}. ${escapeHTML(getStudentName(id))}</strong>
        <span>ID: ${escapeHTML(id)} · Position ${index + 1}</span>
      </div>
      <button class="small-button"
        data-cancel-wait="${escapeHTML(id)}">Cancel</button>
    </div>
  `).join("");

  $("rosterContent").innerHTML = `
    <div class="roster-grid">
      <div class="roster-column">
        <div class="roster-title">
          <span>Enrolled students</span>
          <span>${course.enrolled.length}/${course.capacity}</span>
        </div>
        ${enrolledRows ||
          '<div class="empty-state">No enrolled students.</div>'}
      </div>

      <div class="roster-column">
        <div class="roster-title">
          <span>FIFO waitlist</span>
          <span>${course.waitlist.length} waiting</span>
        </div>
        ${waitingRows ||
          '<div class="empty-state">Waitlist is empty.</div>'}
      </div>
    </div>
    <p class="muted" style="margin-top:12px">
      Available seats: ${course.capacity - course.enrolled.length}
    </p>`;

  $("rosterContent").querySelectorAll("[data-withdraw]").forEach(button => {
    button.addEventListener("click", () =>
      withdrawStudent(course.id, button.dataset.withdraw, false)
    );
  });

  $("rosterContent").querySelectorAll("[data-cancel-wait]").forEach(button => {
    button.addEventListener("click", () =>
      withdrawStudent(course.id, button.dataset.cancelWait, true)
    );
  });
}

// Search for a student across courses
function searchStudent(event) {
  event.preventDefault();

  const id = $("searchId").value.trim();
  lastSearchId = id;

  const matches = state.courses.map(course => {
    let status = "Not registered";

    if (course.enrolled.includes(id)) status = "Enrolled";
    else if (course.waitlist.includes(id)) status = "Waitlisted";

    return { course, status };
  }).filter(item => item.status !== "Not registered");

  $("updateForm").classList.add("hidden");

  if (!state.students[id] || matches.length === 0) {
    $("searchResults").innerHTML =
      '<p class="muted">No student record found.</p>';
    return;
  }

  $("searchResults").innerHTML = `
    <div class="result-item">
      <strong>${escapeHTML(getStudentName(id))} (${escapeHTML(id)})</strong>
      <span class="muted">Registered courses: ${matches.length}</span>
    </div>
    ${matches.map(item => `
      <div class="result-item">
        <strong>${escapeHTML(item.course.name)}</strong>
        <span class="pill ${item.status === "Enrolled" ? "open" : "full"}">
          ${item.status}
        </span>
        <span class="muted"> · Course ID ${escapeHTML(item.course.id)}</span>
      </div>
    `).join("")}`;

  $("updatedName").value = getStudentName(id);
  $("updateForm").classList.remove("hidden");
}

// Update the student's name
function updateStudent(event) {
  event.preventDefault();

  if (!lastSearchId || !state.students[lastSearchId]) {
    return setMessage(
      "registrationMessage", "Search for a student first.", "error"
    );
  }

  const newName = $("updatedName").value.trim();

  if (!newName) return;

  state.students[lastSearchId].name = newName;

  saveState();
  renderAll();

  $("searchId").value = lastSearchId;
  searchStudent(new Event("submit", { cancelable: true }));

  setMessage(
    "registrationMessage",
    "Student record updated successfully."
  );
}

// Add a new course
function addCourse(event) {
  event.preventDefault();
  clearMessage("courseMessage");

  const id = $("newCourseId").value.trim();
  const name = $("newCourseName").value.trim();
  const capacity = Number($("newCourseCapacity").value);

  if (!id || !name ||
      !Number.isInteger(capacity) || capacity < 1) {
    return setMessage(
      "courseMessage",
      "Enter a course ID, name, and positive seat capacity.",
      "error"
    );
  }

  if (getCourse(id)) {
    return setMessage(
      "courseMessage", "Course ID already exists.", "error"
    );
  }

  state.courses.push({
    id: id,
    name: name,
    capacity: capacity,
    enrolled: [],
    waitlist: []
  });

  saveState();
  renderAll();
  $("addCourseForm").reset();

  setMessage("courseMessage", "Course added successfully.");
}

// Refresh all parts of the dashboard
function renderAll() {
  updateSummary();
  renderCourseOptions();
  renderCourses();
  renderRoster();
}

// Connect forms and buttons to their functions
$("registerForm").addEventListener("submit", registerStudent);
$("searchForm").addEventListener("submit", searchStudent);
$("updateForm").addEventListener("submit", updateStudent);
$("addCourseForm").addEventListener("submit", addCourse);

$("courseFilter").addEventListener("input", renderCourses);
$("rosterCourse").addEventListener("change", renderRoster);

$("showAddCourseBtn").addEventListener("click", () => {
  $("addCoursePanel").classList.remove("hidden");
  $("addCoursePanel").scrollIntoView({
    behavior: "smooth", block: "start"
  });
});

$("hideAddCourseBtn").addEventListener("click", () => {
  $("addCoursePanel").classList.add("hidden");
});

// Reset sample data after confirmation
$("resetDemoBtn").addEventListener("click", () => {
  $("confirmDialog").showModal();
});

$("confirmDialog").addEventListener("close", () => {
  if ($("confirmDialog").returnValue === "confirm") {
    state = sampleData();
    saveState();
    lastSearchId = null;

    $("searchResults").innerHTML =
      '<p class="muted">Search results will appear here.</p>';

    $("updateForm").classList.add("hidden");
    clearMessage("registrationMessage");
    clearMessage("courseMessage");

    renderAll();
  }
});

// Initial display
renderAll();
