# Smart Variable Tracker Navigator

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](./LICENSE)

A JMeter GUI plugin for navigating variable extraction and usage across a JMeter test plan. Navigate instantly from variable usage to source extractors and vice versa. Track all variables including underscore-prefixed names and JSR223 script variables.


## 🎯 Problem Statement

In large-scale JMeter test plans with hundreds of samplers across multiple thread groups, performance testers face critical challenges:

- **Manual verification nightmare**: Opening each sampler individually to check correlation and parameterization
- **Missing correlations**: Dynamic values inadvertently left hardcoded, causing test failures
- **Unused extractors**: Variables extracted but never used, cluttering the test plan
- **Lost variable sources**: Difficulty tracking which sampler uses which variable and where it comes from
- **Underscore variables ignored**: Variables like `${_token}`, `${_sessionId}` not detected by standard tools
- **JSR223 script variables invisible**: `vars.get()` and `vars.put()` in scripts not tracked for navigation
- **No visibility**: No high-level view of overall test plan health

**Result**: Hours wasted on manual inspection, test failures in production, and maintenance headaches.

---

## ✨ Solution

This plugin provides **instant visibility** with automated analysis, smart bidirectional navigation, comprehensive JSR223 script support, and complete variable tracking including underscore-prefixed variables. Transform test plan maintenance from hours to minutes.

---

## 🚀 Key Features

### 1. **Inline Status Indicators in Tree View**
Visual C/P status markers directly in the JMeter tree for instant health checks.

**How to use:**
1. Go to **Tools → Show C/P Status in Tree** (checkbox)
2. Status appears next to each element in the tree
3. Auto-updates when you modify the test plan

**Status Indicators:**
- **C:✓** = Correlation configured (extractors found)
- **C:✗** = Correlation missing (no extractors)
- **C:◐** = Partial (some extractors, possible unused)
- **P:✓** = Parameterization configured (CSV/variables used)
- **P:✗** = Parameterization missing (hardcoded values)
- **C:—** = Not applicable (no correlation needed)

**Pros:**
- ✅ Instant visual feedback without opening samplers
- ✅ Spot issues at a glance across entire JMeter test plan
- ✅ Color-coded icons for quick identification
- ✅ Works with Thread Groups, Controllers, and Samplers

---

### 2. **Smart Bidirectional Variable Navigation**
Navigate from usage to source AND from source to usage with double-click support for all variable patterns.

**Usage → Source Navigation:**
1. Enable tree status: **Tools → Show C/P Status in Tree**
2. Select any sampler in the tree
3. In any text field, **double-click** on:
   - `${variableName}` - Standard JMeter variables
   - `${_variableName}` - Underscore-prefixed variables
   - `vars.get("variableName")` - JSR223 script variable reads
   - `vars.get("_variableName")` - Underscore variables in scripts
4. Automatically navigates to the extractor/CSV that creates it

**Source → Usage Navigation:**
1. Select an extractor (JSON, Regex, XPath, etc.)
2. Double-click the variable name in "Names of created variables" field
3. Works with semicolon-delimited lists: `userId;_sessionToken;userName`
4. All samplers using that variable are highlighted in **purple**

**JSR223 Script Support:**
- Double-click on variable names inside `vars.get("varName")` calls
- Works in PreProcessor, PostProcessor, and Sampler scripts
- Navigates to source extractors or CSV configs

**Works with:**
- Text fields (Path, Parameters, Body, Headers)
- Tables (HTTP Arguments, Headers table)
- Extractors (variable name fields with semicolon-delimited lists)
- CSV Data Set Config (comma-delimited variable names)
- JSR223 scripts (both vars.get and vars.put patterns)

**Pros:**
- ✅ Find variable sources in seconds, not minutes
- ✅ Navigate both ways: usage→source and source→usage
- ✅ Full support for underscore-prefixed variables
- ✅ JSR223 script variables fully trackable
- ✅ No more manual searching through dozens of samplers
- ✅ Understand variable flow instantly
- ✅ Verify correlation chains quickly

---

### 3. **Variable Usage Highlighting**
Highlight all samplers that use a specific variable with purple visual indicators.

**How to use:**

**From Extractor:**
1. Select an extractor (JSON, Regex, XPath, XPath2, CSS/JQuery, Boundary)
2. Double-click the "Names of created variables" field
3. For multiple variables (e.g., `userId;_token;userName`), click on specific variable
4. All samplers using that variable are highlighted in **purple**

**From CSV Config:**
1. Select a CSV Data Set Config
2. Double-click a variable name in "Variable Names" field
3. For comma-delimited lists (e.g., `user,pass,_apiKey`), click on specific variable
4. All samplers using that CSV variable are highlighted

**From JSR223 Processor:**
1. Select JSR223 PreProcessor/PostProcessor/Sampler with `vars.put("varName", ...)`
2. Double-click the variable name in the script
3. All usages highlighted in purple (including vars.get() calls)

**Clear highlights:**
- Go to **Tools → Clear Variable Highlights**

**Pros:**
- ✅ Instantly see variable impact across test plan
- ✅ Identify unused extractors (no highlights = unused)
- ✅ Works with underscore variables: `_token`, `_sessionId`, etc.
- ✅ Verify parameterization coverage
- ✅ Visual debugging of correlation chains
- ✅ Tracks vars.get() usage in JSR223 scripts

---

### 4. **Correlation Info Dialog**
View all variables extracted and used by a sampler in one dialog with full underscore support.

**How to use:**
1. Enable tree status
2. Right-click on any sampler with extractors or correlation variables
3. Select **"View / Trace Correlation Variables ⓘ"**
4. Dialog shows:
   - **Variables Extracted** by this sampler (including `_variableName` patterns)
   - **Variables Used** by this sampler (including underscore and JSR223 vars.get)
   - Variable sources (Extractor type, CSV, etc.)

**Alternative:**
- Click the **ⓘ icon** that appears next to samplers with variables

**Pros:**
- ✅ Complete variable overview in one view
- ✅ Shows underscore-prefixed variables correctly
- ✅ Detects vars.get() usage in JSR223 scripts
- ✅ Understand data flow without opening multiple elements
- ✅ Quickly verify correlation chains
- ✅ Navigate to sources with "Go To" buttons

---

### 5. **Find Elements**
Quickly locate all JSR223 processors and extractors across the entire JMeter test plan.

**How to use:**
1. Go to **Tools → Find Elements**
2. Choose:
   - **JSR223 PostProcessors** - Find all post-processors (for correlation)
   - **JSR223 PreProcessors** - Find all pre-processors (for setup/parameterization)
   - **All Extractors** - Find JSON, Regex, Boundary, XPath, XPath2, CSS/JQuery extractors

**Results:**
- Organized by Thread Group
- Shows element name and location
- Click "Navigate" to jump to element
- Multi-select and navigate through results

**Pros:**
- ✅ Find all correlation points instantly
- ✅ Audit extractors across large test plans
- ✅ Identify JSR223 scripts for review
- ✅ No manual tree navigation needed

---

### 6. **Manual Status Override**
Override auto-detected status when you know better than automation.

**How to use:**
1. Right-click on any element in the tree
2. Choose from menu:
   - **C: Mark Correlation Done ✓** - Override to configured
   - **C: Uncheck Correlation** - Override to not configured
   - **P: Mark Parameterization Done ✓**
   - **P: Uncheck Parameterization**
   - **Mark Both Done ✓** - Set both C and P to configured
   - **Mark Both N/A —** - Mark as not applicable
   - **Reset to Auto-Detected** - Clear manual overrides

**Auto-saved:**
- Overrides saved automatically to `.jmx-status.json`
- Persists between JMeter sessions
- Stored alongside your JMX file

**Pros:**
- ✅ Handle complex scenarios automation can't detect
- ✅ Mark intentionally hardcoded values as N/A
- ✅ Override false positives from static analysis
- ✅ Track test plan review progress

---

### 7. **Enhanced Export Report**
Generate comprehensive reports with complete variable tracking and test plan structure.

**How to use:**
1. Open **Tools → C/P Status - Detailed View**
2. Click **"Scan Test Plan"**
3. Click **"Export Report"**
4. Choose filename and location
5. Report saved as detailed text file

**Report Contents:**
```
===============================================
  VARIABLE TRACKING & FLOW REPORT
  Smart Variable Tracker & Navigator
===============================================

SUMMARY STATISTICS:
-------------------
Thread Groups: 5
HTTP Samplers: 47
Extractors: 23
Parameterization Sources: 8
Total Variables Tracked: 31

Correlation Status:
  Configured: 40
  Partial: 5
  Not Configured: 2

===============================================
  TEST PLAN STRUCTURE
===============================================

📋 Test Plan [TestPlan] C:✓ P:✓
  👥 Login Thread Group [ThreadGroup] C:✓ P:✓
    🌐 /login [HTTPSampler] C:✓ P:✓
      ├─ Variables Extracted:
      │  └─ _sessionToken (JSON Extractor)
      ├─ Variables Used:
      │  └─ ${username} from CSV Parameter: users.csv
    🌐 /dashboard [HTTPSampler] C:✓ P:✓
      ├─ Variables Used:
      │  └─ ${_sessionToken} from Extractor: JSON Extractor

===============================================
  VARIABLE DETAILS
===============================================

Variable: ${_sessionToken}
  Source Type: Extractor
  Source Name: JSON Extractor - Token
  Thread Group: Login Thread Group
  Used By (3 samplers):
    • /dashboard (TG: Login Thread Group)
    • /api/data (TG: Login Thread Group)
    • /logout (TG: Login Thread Group)

Variable: ${username}
  Source Type: CSV Parameter
  Source Name: users.csv
  Thread Group: Login Thread Group
  Used By (1 sampler):
    • /login (TG: Login Thread Group)
```

**Pros:**
- ✅ Complete documentation for test plan audits
- ✅ Variable tracking matrix for troubleshooting
- ✅ Identify unused variables instantly
- ✅ Perfect for handover to new team members
- ✅ Thread Group and structure context included
- ✅ Includes underscore-prefixed variables
- ✅ Shows JSR223 script variable usage


### 08. **Rescan Test Plan**
Refresh analysis after making changes without restarting JMeter.

**How to use:**
1. Go to **Tools → Rescan Correlation & Parameterization**
2. Analysis runs in background
3. Tree updates automatically

**Auto-rescan:**
- Automatically rescans when you select different elements
- Picks up new extractors, CSV configs, variables immediately

**Pros:**
- ✅ See changes reflected instantly
- ✅ No need to reopen the plugin window
- ✅ Verify fixes in real-time
- ✅ Iterative development workflow

---

## 📥 Installation

### Prerequisites
- **Java 11+** (JMeter 5.x requirement)
- **JMeter 5.6.3+** (may work with earlier 5.x versions)
- **Maven 3.6+** (for building from source)

### Option 1: Install Pre-built JAR

1. Download the latest JAR from releases:
   ```
   correlation-parameterization-plugin-1.0.0-SNAPSHOT-YYYYMMDD_HHMMSS.jar
   ```

2. Copy to JMeter's plugin directory:
   ```bash
   # Windows
   copy correlation-parameterization-plugin-*.jar %JMETER_HOME%\lib\ext\

   # Linux/Mac
   cp correlation-parameterization-plugin-*.jar $JMETER_HOME/lib/ext/
   ```

3. Restart JMeter

4. Verify installation:
   - Check **Tools** menu for new options
   - Should see: "Show C/P Status in Tree", "Rescan...", "C/P Status - Detailed View", "Find Elements"

### Option 2: Build from Source

```bash
# Clone repository
git clone <repository-url>
cd Jmeter_tickmark_plugin_update5_wo_aggRepNavi

# Build
mvn clean package -DskipTests

# JAR created in target/ directory
# Copy to JMeter
cp target/correlation-parameterization-plugin-*.jar $JMETER_HOME/lib/ext/

# Restart JMeter
```

---

## 📖 Quick Start Guide

### First Time Setup

1. **Install plugin** (see Installation above)

2. **Open JMeter** and load your test plan

3. **Enable inline indicators:**
   - Go to **Tools → Show C/P Status in Tree**
   - Checkbox should be checked
   - Wait a few seconds for analysis to complete

4. **Review the tree:**
   - Look for **C:✗** or **P:✗** markers (problems)
   - Green checkmarks ✓ = good
   - Red crosses ✗ = need attention

### Daily Usage Workflow

**Morning Review:**
```
1. Open test plan in JMeter
2. Tools → Show C/P Status in Tree
3. Tools → C/P Status - Detailed View
4. Click "Scan Test Plan"
5. Review summary statistics
6. Export report for documentation
```

**Finding Issues:**
```
1. In Detailed View, set Filter to "Correlation Issues"
2. Tree shows only problematic samplers
3. Click each to see details
4. Add missing extractors
5. Tools → Rescan to verify fixes
```

**Understanding Variables:**
```
1. Select a sampler with variables
2. Right-click → View / Trace Correlation Variables
3. See all variables used and extracted (including _underscore vars)
4. Click "Go To" to navigate to sources
5. Verify correlation chain is complete
```

**Finding Unused Extractors:**
```
1. Select an extractor
2. Double-click the variable name field
3. If NO purple highlights appear = unused!
4. Either:
   - Remove the extractor (cleanup)
   - Find where it should be used (bug fix)
```

**Testing Underscore Variables:**
```
1. Create variables like ${_token}, ${_userId}, ${_apiKey}
2. Use them in samplers
3. Double-click to navigate to source
4. Verify they appear in Correlation Info dialog
```

**Testing JSR223 Navigation:**
```
1. In JSR223 PreProcessor/PostProcessor, add vars.get("userId")
2. Double-click on "userId" inside the string
3. Navigate to where userId is extracted
4. Works with underscore vars: vars.get("_token")
```

---

## 🏗️ Architecture & Implementation

### High-Level Flow

```
JMeter Test Plan (.jmx)
        │
        ▼
   TestPlanScanner
        │
        ├─────────────┬─────────────┐
        ▼             ▼             ▼
CorrelationAnalyzer  ParameterizationAnalyzer  VariableTracker
        │             │             │
        └─────────────┴─────────────┘
                      ▼
              StatusAggregator
                      ▼
             ElementStatus (Model)
                      │
        ┌─────────────┼─────────────┬─────────────┐
        ▼             ▼             ▼             ▼
  Inline Tree     Details Panel  Summary Panel  Export Report
   Indicators
```

### Core Components

| Component | Purpose |
|-----------|---------|
| `TestPlanScanner` | Traverses test plan tree, collects all elements |
| `CorrelationAnalyzer` | Detects extractors: JSON, Regex, Boundary, XPath, XPath2, CSS, JSR223 |
| `ParameterizationAnalyzer` | Detects CSV, variables, functions |
| `StatusAggregator` | Rolls up status from children to parents |
| `InlineStatusDecorator` | Renders C/P indicators in tree, handles navigation |
| `EnhancedReportExporter` | Generates detailed reports with variable tracking |
| `CorrelationParameterizationPanel` | Main UI with tree, filters, export |

### Detection Logic

**Correlation Detection:**
- ✅ JSON Extractor / JSONPostProcessor
- ✅ Regular Expression Extractor
- ✅ Boundary Extractor
- ✅ XPath Extractor (XPath 1.0)
- ✅ XPath2 Extractor (XPath 2.0)
- ✅ CSS/JQuery Extractor (HtmlExtractor)
- ✅ JSR223 PostProcessor with `vars.put("varName", ...)`
- ✅ JSR223 PreProcessor with `vars.put("varName", ...)`
- ✅ JSR223 Sampler with `vars.put("varName", ...)`
- ✅ BeanShell PostProcessor with variable extraction
- ✅ BeanShell PreProcessor with variable extraction
- ✅ BeanShell Sampler with variable extraction

**Parameterization Detection:**
- ✅ CSV Data Set Config (detects variable names)
- ✅ User Defined Variables
- ✅ JMeter Functions: `${__Random()}`, `${__UUID()}`, `${__time()}`, etc.
- ✅ Variable usage: `${varName}` references (including underscore: `${_varName}`)
- ✅ JSR223 PreProcessor parameterization
- ✅ JSR223 Sampler parameterization

**Variable Usage Tracking:**
- ✅ `${variableName}` patterns in all properties
- ✅ `${_variableName}` patterns (underscore-prefixed)
- ✅ `vars.get("variableName")` in JSR223 scripts
- ✅ `vars.get("_variableName")` in JSR223 scripts (underscore support)
- ✅ Works in PreProcessor, PostProcessor, Sampler

**Status Aggregation Rules:**
- **All children ✓** → Parent = ✓ CONFIGURED
- **Some children ✓** → Parent = ◐ PARTIAL
- **No children ✓** → Parent = ✗ NOT_CONFIGURED
- **Manually marked —** → Parent = — NOT_APPLICABLE

---

**How to Test:**
1. Install the plugin JAR
2. Open `comprehensive-validation-test.jmx` in JMeter
3. Enable: Tools → Show C/P Status in Tree
4. Test each navigation scenario
5. Verify all expected behaviors

---

## 🧪 Supported Extractors & Sources

### Correlation (Extractors)

| Type | Support | Variable Detection | Navigation | Notes |
|------|---------|-------------------|------------|-------|
| JSON Extractor | ✅ Full | ✅ Including underscore | ✅ Bidirectional | Semicolon-delimited lists supported |
| Regular Expression | ✅ Full | ✅ Including underscore | ✅ Bidirectional | Single variable names |
| Boundary Extractor | ✅ Full | ✅ Including underscore | ✅ Bidirectional | Single variable names |
| XPath Extractor | ✅ Full | ✅ Including underscore | ✅ Bidirectional | XPath 1.0 expressions |
| XPath2 Extractor | ✅ Full | ✅ Including underscore | ✅ Bidirectional | XPath 2.0 expressions |
| CSS/JQuery Extractor | ✅ Full | ✅ Including underscore | ✅ Bidirectional | CSS selectors |
| JSR223 PostProcessor | ✅ Pattern | ✅ Including underscore | ✅ Bidirectional | Detects `vars.put("name", ...)` |
| JSR223 PreProcessor | ✅ Pattern | ✅ Including underscore | ✅ Bidirectional | Detects `vars.put("name", ...)` |
| JSR223 Sampler | ✅ Pattern | ✅ Including underscore | ✅ Bidirectional | Detects `vars.put("name", ...)` |
| BeanShell PostProcessor | ✅ Pattern | ✅ Including underscore | ✅ Bidirectional | Detects variable assignments |
| BeanShell PreProcessor | ✅ Pattern | ✅ Including underscore | ✅ Bidirectional | Detects variable assignments |
| BeanShell Sampler | ✅ Pattern | ✅ Including underscore | ✅ Bidirectional | Detects variable assignments |

### Parameterization (Sources)

| Type | Support | Variable Detection | Navigation | Notes |
|------|---------|-------------------|------------|-------|
| CSV Data Set Config | ✅ Full | ✅ Including underscore | ✅ Bidirectional | Comma-delimited lists supported |
| User Defined Variables | ✅ Full | ✅ Including underscore | ✅ Bidirectional | All UDVs tracked |
| JMeter Functions | ✅ Full | ✅ Standard functions | ❌ N/A | `__Random, __UUID, __time`, etc. |
| JSR223 PreProcessor | ✅ Pattern | ✅ Including underscore | ✅ Bidirectional | Detects `vars.put` patterns |
| Variable References | ✅ Full | ✅ Including underscore | ✅ Bidirectional | `${varName}` and `${_varName}` |
| vars.get() in Scripts | ✅ Full | ✅ Including underscore | ✅ Usage tracking | Navigates to source extractor |

### Variable Usage Detection

| Pattern | Support | Location | Example |
|---------|---------|----------|---------|
| `${varName}` | ✅ Full | All properties | Standard JMeter syntax |
| `${_varName}` | ✅ Full | All properties | Underscore-prefixed |
| `vars.get("varName")` | ✅ Full | JSR223 scripts | Navigation to source |
| `vars.get("_varName")` | ✅ Full | JSR223 scripts | Underscore in scripts |
| `vars.put("varName", ...)` | ✅ Full | JSR223 scripts | Creates new variable |
| `vars.put("_varName", ...)` | ✅ Full | JSR223 scripts | Underscore creation |


### Project Structure

```
src/
├── main/java/com/performance/jmeter/correlation/
│   ├── analyzer/               # Detection logic
│   │   ├── CorrelationAnalyzer.java       # Extractor detection (JSON, Regex, XPath, etc.)
│   │   └── ParameterizationAnalyzer.java  # CSV, variables, functions
│   ├── model/                  # Data models
│   │   ├── ElementStatus.java
│   │   ├── ConfigurationStatus.java
│   │   └── DetectedItem.java
│   ├── scanner/                # Tree traversal
│   │   ├── TestPlanScanner.java           # Main scanner with variable tracking
│   │   └── StatusAggregator.java
│   ├── ui/                     # User interface
│   │   ├── InlineStatusDecorator.java     # Tree indicators + navigation
│   │   ├── CorrelationParameterizationPanel.java  # Main window
│   │   ├── EnhancedReportExporter.java    # Report generation
│   │   └── CorrelationParameterizationMenuCreator.java  # Menu integration
│   └── util/                   # Utilities
│       └── OverridePersistence.java
└── test/
    ├── java/                   # Unit tests
    └── resources/              # Test JMX files
        ├── sample-test-plan.jmx
        └── comprehensive-validation-test.jmx
```

### Key Files for Implementation

| File | Lines | Key Implementation |
|------|-------|-------------------|
| `CorrelationAnalyzer.java` | 106-162 | XPath/XPath2/CSS extractor detection |
| `TestPlanScanner.java` | 24, 293-331 | Variable usage pattern (underscore fix + vars.get) |
| `ParameterizationAnalyzer.java` | 23 | Variable reference pattern (underscore fix) |
| `InlineStatusDecorator.java` | 37, 844-896 | Navigation patterns (vars.get + underscore) |



**Property Name Patterns:**
- JSON: `JSONPostProcessor.referenceNames` (semicolon-delimited)
- Regex: `RegexExtractor.refname`
- XPath: `XPathExtractor.refname`
- XPath2: `XPath2Extractor.refname`
- CSS: `HtmlExtractor.refname`
- Boundary: `BoundaryExtractor.refname`

---

## 🤝 Contributing

We welcome contributions! Here's how:

1. **Fork the repository**
2. **Create feature branch:** `git checkout -b feature/your-feature`
3. **Make changes** with tests
4. **Run tests:** `mvn test`
5. **Commit:** `git commit -m "Add: your feature description"`
6. **Push:** `git push origin feature/your-feature`
7. **Create Pull Request**

### Code Style
- Follow existing code patterns
- Add JavaDoc for public methods
- Keep methods under 50 lines
- Write unit tests for new analyzers

---

## 📝 Changelog

### v1.0.0-SNAPSHOT (Current - September 2026)

**Major Features:**
- ✅ Inline C/P status indicators in tree
- ✅ Detailed analysis view with filters
- ✅ Smart bidirectional variable navigation (usage↔source)
- ✅ Variable usage highlighting (purple indicators)
- ✅ Enhanced export report with variable tracking
- ✅ Find elements (JSR223, extractors)
- ✅ Manual status override with persistence
- ✅ Correlation info dialog with "Go To" navigation
- ✅ Rescan functionality

**Extractor Support:**
- ✅ JSON Extractor / JSONPostProcessor
- ✅ Regular Expression Extractor
- ✅ Boundary Extractor
- ✅ XPath Extractor (NEW)
- ✅ XPath2 Extractor (NEW)
- ✅ CSS/JQuery Extractor (HtmlExtractor) (NEW)
- ✅ JSR223 PostProcessor with vars.put()
- ✅ JSR223 PreProcessor with vars.put() (NEW)
- ✅ JSR223 Sampler with vars.put() (NEW)
- ✅ BeanShell variants (all types)

**Variable Detection Enhancements:**
- ✅ Underscore-prefixed variables fully supported (${_token}, ${_userId})
- ✅ vars.get() detection in JSR223 scripts for navigation (NEW)
- ✅ Semicolon-delimited variable lists in extractors (NEW)
- ✅ Comma-delimited variable lists in CSV configs
- ✅ All variable patterns detected in Correlation Info dialog

**Bug Fixes:**
- ✅ Fixed regex patterns to support underscore prefix (3 files)
- ✅ Fixed JSR223 PostProcessor navigation
- ✅ Fixed multiple variable names in extractor fields
- ✅ Fixed vars.get() not tracked for usage detection

---

## 💬 Support

**Issues:** Report bugs and feature requests via GitHub Issues

**Questions:** Tag issues with `question` label

**Feature Requests:** Tag with `enhancement` label

---

## 🙏 Acknowledgments

- Built for the Apache JMeter community
- Inspired by common pain points in large-scale performance testing
- Thanks to all contributors and testers

---

## 🎯 Roadmap

### Completed ✅

- [x] Inline status indicators in tree
- [x] Detailed analysis view
- [x] Smart variable navigation (bidirectional)
- [x] Variable usage highlighting
- [x] Enhanced export report
- [x] Find elements functionality
- [x] Manual status override
- [x] XPath/XPath2/CSS extractor support
- [x] Underscore variable support
- [x] vars.get() navigation in JSR223
- [x] Multiple variables in extractor names
- [x] Complete JSR223 processor type coverage

### Planned Features

- [ ] HTML export format with interactive charts
- [ ] Variable flow diagram visualization
- [ ] Integration with JMeter Plugins Manager
- [ ] Real-time test plan health monitoring
- [ ] Hardcoded value detection heuristics
- [ ] Export to Excel format
- [ ] Custom extractor plugins support
- [ ] Bulk edit operations
- [ ] AI-powered correlation suggestions

### Future Enhancements

- Performance optimization for 1000+ sampler test plans
- Cloud-based report sharing
- Test plan comparison (before/after)
- Integration with CI/CD pipelines
- Mobile app for review on the go

---

**Made with ❤️ for the Performance Engineering Community**

*Star ⭐ this repo if you find it useful!*
