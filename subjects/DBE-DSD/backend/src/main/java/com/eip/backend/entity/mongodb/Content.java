package com.eip.backend.entity.mongodb;
import org.springframework.data.mongodb.core.mapping.Field;
public class Content {
    @Field("raw_text") private String rawText;
    private String language;
    @Field("word_count") private Integer wordCount;
    @Field("character_count") private Integer characterCount;
    // Getters and Setters
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }
    public Integer getCharacterCount() { return characterCount; }
    public void setCharacterCount(Integer characterCount) { this.characterCount = characterCount; }
}
