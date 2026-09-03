# JMeter Smart variable detector Plugin

A powerful JMeter GUI plugin that provides **real-time visual indicators** and **intelligent analysis** for Correlation and Parameterization across your entire test plan. Save hours of manual verification with instant status visibility, variable tracking, and smart navigation.

[![Java](https://img.shields.io/badge/Java-11+-orange.svg)](https://www.oracle.com/java/)
[![JMeter](https://img.shields.io/badge/JMeter-5.6.3+-red.svg)](https://jmeter.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## 🎯 Problem Statement

In large-scale JMeter test plans with hundreds of samplers across multiple thread groups, performance testers face critical challenges:

- **Manual verification nightmare**: Opening each sampler individually to check correlation and parameterization
- **Missing correlations**: Dynamic values inadvertently left hardcoded, causing test failures
- **Unused extractors**: Variables extracted but never used, cluttering the test plan
- **Lost variable sources**: Difficulty tracking which sampler uses which variable and where it comes from
- **No visibility**: No high-level view of overall test plan health

**Result**: Hours wasted on manual inspection, test failures in production, and maintenance headaches.

---

## ✨ Solution

This plugin provides **instant visibility** with automated analysis, smart navigation, and comprehensive reporting. Transform test plan maintenance from hours to minutes.

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
- ✅ Spot issues at a glance across entire test plan
- ✅ Color-coded icons for quick identification
- ✅ Works with Thread Groups, Controllers, and Samplers

---

### 2. **Detailed Analysis View**
Comprehensive dashboard with tree structure, details panel, and summary statistics.

**How to use:**
1. Go to **Tools → C/P Status - Detailed View**
2. Click **"Scan Test Plan"** button
3. Browse the tree to see detailed status
4. Click any element to see details in right panel

**What you get:**
- **Left Panel**: Full tree with status indicators
- **Center Panel**: Element details (extractors, variables, status)
- **Right Panel**: Summary statistics (Thread Groups, Samplers, Coverage)

**Pros:**
- ✅ Complete overview of test plan health
- ✅ Drill down to specific issues quickly
- ✅ Statistics help prioritize work
- ✅ Filter by status to focus on problems

---

### 3. **Smart Variable Navigation**
Double-click any variable to instantly jump to its source (extractor or CSV).

**How to use:**
1. Enable tree status: **Tools → Show C/P Status in Tree**
2. Select any sampler in the tree
3. In the right panel, **double-click** on any field containing `${variableName}`
4. Automatically navigates to the extractor/CSV that creates it

**Works with:**
- Text fields (Path, Parameters, Body, Headers)
- Tables (HTTP Arguments, Headers table)
- Extractors (variable name fields)
- CSV Data Set Config (variable names field)

**Pros:**
- ✅ Find variable sources in seconds, not minutes
- ✅ No more manual searching through dozens of samplers
- ✅ Understand variable flow instantly
- ✅ Verify correlation chains quickly

---

### 4. **Variable Usage Highlighting**
Highlight all samplers that use a specific variable with purple visual indicators.

**How to use:**

**From Extractor:**
1. Select an extractor (JSON, Regex, JSR223 PostProcessor)
2. Double-click the "Name of created variable" field
3. All samplers using that variable are highlighted in **purple**

**From CSV Config:**
1. Select a CSV Data Set Config
2. Double-click a variable name in "Variable Names" field
3. All samplers using that CSV variable are highlighted

**From JSR223 PostProcessor:**
1. Select JSR223 PostProcessor with `vars.put("varName", ...)`
2. Double-click the variable name in the script
3. All usages highlighted in purple

**Clear highlights:**
- Go to **Tools → Clear Variable Highlights**

**Pros:**
- ✅ Instantly see variable impact across test plan
- ✅ Identify unused extractors (no highlights = unused)
- ✅ Verify parameterization coverage
- ✅ Visual debugging of correlation chains

---

### 5. **Correlation Info Dialog**
View all variables extracted and used by a sampler in one dialog.

**How to use:**
1. Enable tree status
2. Right-click on any sampler with extractors or correlation variables
3. Select **"View / Trace Correlation Variables ⓘ"**
4. Dialog shows:
   - **Variables Extracted** by this sampler
   - **Variables Used** by this sampler
   - Variable sources (Extractor type, CSV, etc.)

**Alternative:**
- Click the **ⓘ icon** that appears next to samplers with variables

**Pros:**
- ✅ Complete variable overview in one view
- ✅ Understand data flow without opening multiple elements
- ✅ Quickly verify correlation chains
- ✅ Navigate to sources with "Go To" buttons

---

### 6. **Find Elements**
Quickly locate all JSR223 processors and extractors across the entire test plan.

**How to use:**
1. Go to **Tools → Find Elements**
2. Choose:
   - **JSR223 PostProcessors** - Find all post-processors (for correlation)
   - **JSR223 PreProcessors** - Find all pre-processors (for setup/parameterization)
   - **All Extractors** - Find JSON, Regex, Boundary, XPath extractors

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

### 7. **Manual Status Override**
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

### 8. **Enhanced Export Report**
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
  CORRELATION & PARAMETERIZATION REPORT
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
      │  └─ sessionToken (JSON Extractor)
      ├─ Variables Used:
      │  └─ ${username} from CSV Parameter: users.csv
    🌐 /dashboard [HTTPSampler] C:✓ P:✓
      ├─ Variables Used:
      │  └─ ${sessionToken} from Extractor: JSON Extractor

===============================================
  VARIABLE DETAILS
===============================================

Variable: ${sessionToken}
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

---

### 9. **Filter & Search**
Quickly find issues with powerful filtering and search.

**How to use:**

**Filter Options:**
1. Open Detailed View
2. Use **Filter** dropdown:
   - **All** - Show everything
   - **Correlation Issues** - Only items with correlation problems
   - **Parameterization Issues** - Only param problems
   - **Partial** - Items partially configured
   - **Not Configured** - Items missing configuration

**Search:**
1. Enter sampler name in **Search** field
2. Click **Go**
3. Tree filters to matching items only

**Pros:**
- ✅ Focus on problems, ignore what's working
- ✅ Quickly locate specific samplers
- ✅ Prioritize work by issue type
- ✅ Efficient for large test plans (100+ samplers)

---

### 10. **Rescan Test Plan**
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
3. See all variables used and extracted
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

---

## 🏗️ Architecture

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
| `CorrelationAnalyzer` | Detects extractors: JSON, Regex, Boundary, JSR223 |
| `ParameterizationAnalyzer` | Detects CSV, variables, functions |
| `StatusAggregator` | Rolls up status from children to parents |
| `InlineStatusDecorator` | Renders C/P indicators in tree |
| `EnhancedReportExporter` | Generates detailed reports with variable tracking |
| `CorrelationParameterizationPanel` | Main UI with tree, filters, export |

### Detection Logic

**Correlation Detection:**
- ✅ JSON Extractor / JSONPostProcessor
- ✅ Regular Expression Extractor
- ✅ Boundary Extractor
- ✅ XPath Extractor
- ✅ XPath2 Extractor
- ✅ CSS/JQuery Extractor
- ✅ JSR223 PostProcessor with `vars.put("varName", ...)`
- ✅ BeanShell PostProcessor with variable extraction

**Parameterization Detection:**
- ✅ CSV Data Set Config (detects variable names)
- ✅ User Defined Variables
- ✅ JMeter Functions: `${__Random()}`, `${__UUID()}`, `${__time()}`, etc.
- ✅ Variable usage: `${varName}` references
- ✅ JSR223 PreProcessor parameterization

**Status Aggregation Rules:**
- **All children ✓** → Parent = ✓ CONFIGURED
- **Some children ✓** → Parent = ◐ PARTIAL
- **No children ✓** → Parent = ✗ NOT_CONFIGURED
- **Manually marked —** → Parent = — NOT_APPLICABLE

---

## 🎓 Use Cases & Benefits

### Use Case 1: **Pre-Production Test Plan Audit**

**Scenario:** You have a 200-sampler test plan ready for load testing, need to verify everything is parameterized.

**Without Plugin:**
- 4-6 hours manually opening each sampler
- High risk of missing issues
- No documentation trail

**With Plugin:**
```
1. Tools → C/P Status - Detailed View [30 seconds]
2. Scan Test Plan [10 seconds]
3. Filter: "Parameterization Issues" [5 seconds]
4. Export Report for audit trail [10 seconds]
5. Fix 3 missing CSV configs [10 minutes]
6. Rescan to verify [10 seconds]

Total Time: 15 minutes
Documentation: Complete audit report generated
```

**Benefit:** 95% time savings, zero missed issues

---

### Use Case 2: **Debugging Test Failures**

**Scenario:** Test is failing because variable `${orderId}` is empty in checkout flow.

**Without Plugin:**
- Search through 50 samplers to find where `orderId` is extracted
- Check if it's used before it's extracted
- No clear correlation chain visibility

**With Plugin:**
```
1. Enable inline indicators
2. Search for "checkout" sampler
3. Right-click → View Correlation Variables
4. See that ${orderId} is used but source shows "unknown"
5. Tools → Find Elements → All Extractors
6. Find the extractor is AFTER the usage (order wrong!)
7. Drag extractor above checkout sampler
8. Rescan - shows ✓ configured

Total Time: 3 minutes
```

**Benefit:** Instant root cause identification

---

### Use Case 3: **Test Plan Cleanup**

**Scenario:** Test plan has grown to 300 samplers over 2 years, cluttered with unused extractors.

**Without Plugin:**
- No way to know which extractors are unused
- Fear of breaking something by removing extractors

**With Plugin:**
```
1. Tools → Find Elements → All Extractors
2. For each extractor:
   - Double-click variable name field
   - If NO purple highlights = UNUSED
   - Right-click → Delete
3. Cleanup 15 unused extractors in 10 minutes

Total Time: 10 minutes
Risk: Zero (visual confirmation before deletion)
```

**Benefit:** Cleaner test plan, faster execution, easier maintenance

---

## 🧪 Supported Extractors & Sources

### Correlation (Extractors)

| Type | Support | Notes |
|------|---------|-------|
| JSON Extractor | ✅ Full | JSONPath expressions detected |
| Regular Expression | ✅ Full | Regex patterns analyzed |
| Boundary Extractor | ✅ Full | Left/right boundary detected |
| XPath Extractor | ✅ Full | XPath 1.0 expressions |
| XPath2 Extractor | ✅ Full | XPath 2.0 expressions |
| CSS/JQuery Extractor | ✅ Full | CSS selectors detected |
| JSR223 PostProcessor | ✅ Pattern | Detects `vars.put("name", ...)` |
| BeanShell PostProcessor | ✅ Pattern | Detects variable assignments |

### Parameterization (Sources)

| Type | Support | Notes |
|------|---------|-------|
| CSV Data Set Config | ✅ Full | All variables detected |
| User Defined Variables | ✅ Full | All UDVs tracked |
| JMeter Functions | ✅ Full | `__Random, __UUID, __time`, etc. |
| JSR223 PreProcessor | ✅ Pattern | Detects `vars.put` patterns |
| Variable References | ✅ Full | `${varName}` usage tracked |

---

## ⚠️ Known Limitations

### Analysis Limitations

1. **Static Analysis Only**
   - Cannot detect runtime-generated variable names
   - Example: `vars.put("var" + i, value)` not detected
   - Workaround: Use manual override

2. **JSR223 Script Complexity**
   - Only detects simple `vars.put("name", ...)` patterns
   - Complex Groovy logic may not be fully analyzed
   - Workaround: Add comments or use manual override

3. **Cross-Thread-Group Variables**
   - Variables passed between thread groups via properties may not be tracked
   - Workaround: Document in manual override comments

4. **Conditional Logic**
   - If/else blocks in extractors not analyzed
   - May show false positives/negatives
   - Workaround: Review conditional extractors manually

### UI Limitations

1. **Large Test Plans (1000+ samplers)**
   - Initial scan may take 5-10 seconds
   - Tree rendering may be slow
   - Workaround: Use filters to reduce visible items

2. **Manual Override Reset**
   - Renaming samplers resets manual overrides
   - Workaround: Avoid renaming after overrides, or reapply

---

## 🔧 Configuration

### Settings File

Plugin creates `.jmx-status.json` next to your JMX file to store:
- Manual status overrides
- Per-element override reasons
- Timestamp of last update

**Example:**
```json
{
  "/TestPlan/LoginTG/POST-Login": {
    "correlationOverride": "CONFIGURED",
    "parameterizationOverride": null,
    "reason": "Uses JavaScript for dynamic token generation",
    "lastModified": "2026-09-03T10:30:00Z"
  }
}
```

**Location:** Same directory as your `.jmx` file

**Version Control:** Add `*.jmx-status.json` to `.gitignore` if overrides are personal

---

## 🛠️ Development

### Build & Test

```bash
# Full build with tests
mvn clean test package

# Skip tests (faster)
mvn clean package -DskipTests

# Run specific test
mvn test -Dtest=CorrelationAnalyzerTest

# Generate test coverage report
mvn clean test jacoco:report
```

### Project Structure

```
src/
├── main/java/com/performance/jmeter/correlation/
│   ├── analyzer/               # Detection logic
│   │   ├── CorrelationAnalyzer.java
│   │   └── ParameterizationAnalyzer.java
│   ├── model/                  # Data models
│   │   ├── ElementStatus.java
│   │   ├── ConfigurationStatus.java
│   │   └── DetectedItem.java
│   ├── scanner/                # Tree traversal
│   │   ├── TestPlanScanner.java
│   │   └── StatusAggregator.java
│   ├── ui/                     # User interface
│   │   ├── InlineStatusDecorator.java       # Tree indicators
│   │   ├── CorrelationParameterizationPanel.java  # Main window
│   │   ├── EnhancedReportExporter.java      # Report generation
│   │   └── CorrelationParameterizationMenuCreator.java  # Menu integration
│   └── util/                   # Utilities
│       └── OverridePersistence.java
└── test/
    ├── java/                   # Unit tests
    └── resources/              # Test JMX files
```

### Adding New Extractor Support

```java
// In CorrelationAnalyzer.java
public List<DetectedItem> detectExtractors(TestElement element) {
    List<DetectedItem> items = new ArrayList<>();
    
    // Add your new extractor type
    if (element.getClass().getName().contains("YourNewExtractor")) {
        String varName = element.getPropertyAsString("your.var.property");
        items.add(new DetectedItem(
            "Your Extractor Type",
            varName,
            "XPath: " + element.getPropertyAsString("xpath"),
            true
        ));
    }
    
    return items;
}
```

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

### v1.0.0-SNAPSHOT (Current)
- ✅ Inline C/P status indicators in tree
- ✅ Detailed analysis view with filters
- ✅ Smart variable navigation (double-click)
- ✅ Variable usage highlighting (purple indicators)
- ✅ Enhanced export report with variable tracking
- ✅ Find elements (JSR223, extractors)
- ✅ Manual status override with persistence
- ✅ Correlation info dialog
- ✅ Rescan functionality
- ✅ Support for all major extractor types

---

## 📄 License

Apache License 2.0 - See [LICENSE](LICENSE) file for details

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

### Planned Features

- [ ] HTML export format with interactive charts
- [ ] AI-powered correlation suggestions
- [ ] Variable flow diagram visualization
- [ ] Integration with JMeter Plugins Manager
- [ ] Real-time test plan health monitoring
- [ ] Hardcoded value detection heuristics
- [ ] Export to Excel format
- [ ] Custom extractor plugins support
- [ ] Bulk edit operations

### Future Enhancements

- Performance optimization for 1000+ sampler test plans
- Cloud-based report sharing
- Test plan comparison (before/after)
- Integration with CI/CD pipelines
- Mobile app for review on the go

---

**Made with ❤️ for the Performance Engineering Community**

*Star ⭐ this repo if you find it useful!*
