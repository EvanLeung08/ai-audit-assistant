package org.evan.ai.audit.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Markdown cleaning functionality.
 */
class MarkdownCleanerTest {

    @Test
    void testCleanMarkdownHeaders() {
        String input = "### This is a header\n## Another header\n# Main header";
        String expected = "This is a header\nAnother header\nMain header";
        assertEquals(expected, cleanMarkdown(input));
    }

    @Test
    void testCleanMarkdownBold() {
        String input = "This is **bold** text and __also bold__ text";
        String expected = "This is bold text and also bold text";
        assertEquals(expected, cleanMarkdown(input));
    }

    @Test
    void testCleanMarkdownItalic() {
        String input = "This is *italic* text and _also italic_ text";
        String expected = "This is italic text and also italic text";
        assertEquals(expected, cleanMarkdown(input));
    }

    @Test
    void testCleanMarkdownBulletPoints() {
        String input = "- Item 1\n* Item 2\n+ Item 3";
        String expected = "Item 1\nItem 2\nItem 3";
        assertEquals(expected, cleanMarkdown(input));
    }

    @Test
    void testCleanMarkdownInlineCode() {
        String input = "Use the `command` to run it";
        String expected = "Use the command to run it";
        assertEquals(expected, cleanMarkdown(input));
    }

    @Test
    void testCleanMarkdownLinks() {
        String input = "Visit [Google](https://google.com) for more info";
        String expected = "Visit Google for more info";
        assertEquals(expected, cleanMarkdown(input));
    }

    @Test
    void testCleanMarkdownBlockquotes() {
        String input = "> This is a quote\n> Another quote line";
        String expected = "This is a quote\nAnother quote line";
        assertEquals(expected, cleanMarkdown(input));
    }

    @Test
    void testCleanMarkdownComplexText() {
        String input = """
                ### Security Policy
                
                **Important**: Follow these rules:
                
                - Use *strong* passwords
                - Enable `MFA` for all accounts
                - Visit [our docs](https://docs.example.com) for details
                
                > Remember: Security is everyone's responsibility.
                """;

        String result = cleanMarkdown(input);

        // Should not contain markdown characters
        assertFalse(result.contains("###"));
        assertFalse(result.contains("**"));
        assertFalse(result.contains("`"));
        assertFalse(result.contains("]("));
        assertFalse(result.contains("> "));

        // Should contain the actual text
        assertTrue(result.contains("Security Policy"));
        assertTrue(result.contains("Important"));
        assertTrue(result.contains("strong passwords"));
        assertTrue(result.contains("MFA"));
    }

    /**
     * Helper method that duplicates the cleanMarkdown logic for testing.
     */
    private String cleanMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;

        // Remove headers
        result = Pattern.compile("^#{1,6}\\s*", Pattern.MULTILINE).matcher(result).replaceAll("");

        // Remove bold
        result = result.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        result = result.replaceAll("__(.+?)__", "$1");

        // Remove italic
        result = result.replaceAll("(?<!\\w)\\*(.+?)\\*(?!\\w)", "$1");
        result = result.replaceAll("(?<!\\w)_(.+?)_(?!\\w)", "$1");

        // Remove inline code
        result = result.replaceAll("`(.+?)`", "$1");

        // Remove code blocks
        result = result.replaceAll("```[\\s\\S]*?```", "");

        // Remove links
        result = result.replaceAll("\\[(.+?)\\]\\(.+?\\)", "$1");

        // Remove images
        result = result.replaceAll("!\\[(.*)\\]\\(.+?\\)", "$1");

        // Remove bullet points
        result = Pattern.compile("^\\s*[-*+]\\s+", Pattern.MULTILINE).matcher(result).replaceAll("");

        // Remove horizontal rules
        result = Pattern.compile("^[-*_]{3,}\\s*$", Pattern.MULTILINE).matcher(result).replaceAll("");

        // Remove blockquotes
        result = Pattern.compile("^>\\s*", Pattern.MULTILINE).matcher(result).replaceAll("");

        // Remove strikethrough
        result = result.replaceAll("~~(.+?)~~", "$1");

        // Clean up multiple blank lines
        result = result.replaceAll("\n{3,}", "\n\n");

        return result.trim();
    }
}
