package art.moodframe.ui;

/**
 * The one inset of the boxes of the main window and the brainstorm dialog: from the left edge of a box to the line
 * where its content starts (the names of a list, the chat entries and the first column of images, the bubbles, the
 * text of a form and of the input fields). The heading of a box starts on this line, too.
 *
 * @since 2.0.0
 */
public final class BoxInset {

    /*  From the left edge of a box to its content: the text inset of an input field of the theme (1 px border, 2 px
        padding, 12 px content padding), so headings, content and the text in the fields share one line  */
    public static final double LEFT = 15.0;

    /*  From the right edge of the chat area to the end of its content: the right edge of the view switch in the chat
        header, of the chat entries and of the last column of images  */
    public static final double RIGHT = 20.0;

    /*  What a list puts in front of the content of a row: its 1 px border and the 7 px (0.5 em) cell padding of the
        theme  */
    public static final double LIST = 8.0;

    /*  The theme pads the text of a text area 12 px inside its content area  */
    public static final double TEXT = 12.0;

    private BoxInset() {
    }
}
