#!/usr/bin/env bash
# =============================================================================
# AMG Intelligent Document Platform — Diagram Image Generator
# =============================================================================
# Generates high-resolution PNG and SVG images from Mermaid .mmd source files
# using @mermaid-js/mermaid-cli (mmdc).
#
# Usage:
#   ./generate-diagrams.sh          # Generate all diagrams (PNG + SVG)
#   ./generate-diagrams.sh png      # Generate PNG only
#   ./generate-diagrams.sh svg      # Generate SVG only
#   ./generate-diagrams.sh clean    # Remove generated images
#
# Prerequisites:
#   npm install -g @mermaid-js/mermaid-cli
#   # or use npx (no global install needed)
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_DIR="$SCRIPT_DIR"
OUTPUT_DIR="$SCRIPT_DIR/output"
CONFIG="$SOURCE_DIR/mermaid-config.json"

# High-res settings for investor presentations
SCALE=3          # 3x scale → ~4K resolution
WIDTH=2400       # Base width in pixels
BG_COLOR="#0f172a"  # Dark background matching theme

# Diagram files and their display names
declare -a DIAGRAMS=(
    "01-platform-architecture:Platform Architecture"
    "02-e2e-processing-flow:End-to-End Processing Flow"
    "03-knowledge-base:Centralized Knowledge Base"
    "04-amg-flow-config:AIP Questionnaire Flow"
    "05-deployment:Production Deployment"
    "06-product-layers:Product Layer Model"
    "07-feedback-loop:User Feedback & Continuous Improvement"
    "08-review-approval:Review & Approval Workflow"
    "09-kb-auto-update:Knowledge Base Auto-Update"
)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

print_header() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║  AMG Intelligent Document Platform — Diagram Generator  ║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

check_mmdc() {
    if command -v mmdc &> /dev/null; then
        echo -e "${GREEN}✓${NC} mmdc found: $(which mmdc)"
        return 0
    fi

    # Check if npx can find it
    if npx --yes -p @mermaid-js/mermaid-cli mmdc --version &> /dev/null 2>&1; then
        echo -e "${YELLOW}⚠${NC} mmdc not globally installed, will use npx"
        return 0
    fi

    echo -e "${RED}✗${NC} mermaid-cli not found."
    echo ""
    echo "  Install with one of:"
    echo "    npm install -g @mermaid-js/mermaid-cli"
    echo "    brew install mermaid-cli"
    echo ""
    echo "  Or the script will use npx automatically."
    exit 1
}

get_mmdc_cmd() {
    if command -v mmdc &> /dev/null; then
        echo "mmdc"
    else
        echo "npx --yes -p @mermaid-js/mermaid-cli mmdc"
    fi
}

generate_diagram() {
    local src="$1"
    local name="$2"
    local format="$3"
    local output_file="$OUTPUT_DIR/${name}.${format}"

    local MMDC
    MMDC=$(get_mmdc_cmd)

    local extra_args=""
    if [[ "$format" == "png" ]]; then
        extra_args="--scale $SCALE --width $WIDTH --backgroundColor $BG_COLOR"
    fi

    echo -ne "  ${BLUE}→${NC} Generating ${format^^}: ${name}... "

    if $MMDC \
        --input "$src" \
        --output "$output_file" \
        --configFile "$CONFIG" \
        --puppeteerConfigFile <(echo '{"args":["--no-sandbox"]}') \
        $extra_args \
        2>/dev/null; then
        local size
        size=$(du -h "$output_file" | cut -f1 | xargs)
        echo -e "${GREEN}✓${NC} ($size)"
    else
        echo -e "${RED}✗ Failed${NC}"
        return 1
    fi
}

do_clean() {
    echo -e "${YELLOW}Cleaning output directory...${NC}"
    if [[ -d "$OUTPUT_DIR" ]]; then
        rm -rf "$OUTPUT_DIR"
        echo -e "${GREEN}✓${NC} Cleaned: $OUTPUT_DIR"
    else
        echo -e "${YELLOW}⚠${NC} Nothing to clean"
    fi
}

do_generate() {
    local format="${1:-all}"

    print_header
    check_mmdc

    mkdir -p "$OUTPUT_DIR"

    local total=0
    local success=0
    local failed=0

    for entry in "${DIAGRAMS[@]}"; do
        local file="${entry%%:*}"
        local label="${entry#*:}"
        local src="$SOURCE_DIR/${file}.mmd"

        if [[ ! -f "$src" ]]; then
            echo -e "  ${RED}✗${NC} Source not found: ${file}.mmd"
            ((failed++))
            continue
        fi

        echo -e "\n${CYAN}▸ ${label}${NC} (${file}.mmd)"

        if [[ "$format" == "all" || "$format" == "png" ]]; then
            ((total++))
            if generate_diagram "$src" "$file" "png"; then
                ((success++))
            else
                ((failed++))
            fi
        fi

        if [[ "$format" == "all" || "$format" == "svg" ]]; then
            ((total++))
            if generate_diagram "$src" "$file" "svg"; then
                ((success++))
            else
                ((failed++))
            fi
        fi
    done

    echo ""
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "  Total: ${total}  ${GREEN}Success: ${success}${NC}  ${RED}Failed: ${failed}${NC}"
    echo -e "  Output: ${OUTPUT_DIR}"
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""

    if [[ "$success" -gt 0 ]]; then
        echo -e "${GREEN}💡 Tip:${NC} Open the output folder:"
        echo "   open $OUTPUT_DIR"
        echo ""
    fi
}

# =============================================================================
# Main
# =============================================================================
case "${1:-all}" in
    clean)
        do_clean
        ;;
    png|svg|all)
        do_generate "${1:-all}"
        ;;
    help|-h|--help)
        echo "Usage: $0 [png|svg|all|clean|help]"
        echo ""
        echo "  all    Generate both PNG and SVG (default)"
        echo "  png    Generate PNG only (high-res, 3x scale)"
        echo "  svg    Generate SVG only (vector, scalable)"
        echo "  clean  Remove generated images"
        echo "  help   Show this help message"
        ;;
    *)
        echo -e "${RED}Unknown option: $1${NC}"
        echo "Usage: $0 [png|svg|all|clean|help]"
        exit 1
        ;;
esac
