package com.sap.util;

//JAVA program for implementation of KMP pattern 
//searching algorithm 


public class SearchTextMatcher {
	
	public static boolean matchSearchText(String searchString, String textContent) {
		int M = searchString.length();
		int N = textContent.length();

		// create lps[] that will hold the longest prefix suffix values for pattern
		int lps[] = new int[M];
		int j = 0; // index for searchString[]

		// Preprocess the pattern (calculate lps[] array)
		computeLPSArray(searchString, M, lps);

		int i = 0; // index for textContent[]
		while (i < N) {
			if (searchString.charAt(j) == textContent.charAt(i)) {
				j++;
				i++;
			}
			if (j == M) {
				System.out.println("Found pattern " + "at index " + (i - j));
				j = lps[j - 1];
				return true;
			}// mismatch after j matches
			else if (i < N && searchString.charAt(j) != textContent.charAt(i)) {
				// Do not match lps[0..lps[j-1]] characters, they will match anyway
				if (j != 0)
					j = lps[j - 1];
				else
					i = i + 1;
			}
		}
		return false;
	}

	static void computeLPSArray(String searchString, int M, int lps[]) {
		// length of the previous longest prefix suffix
		int len = 0;
		int i = 1;
		lps[0] = 0; // lps[0] is always 0

		// the loop calculates lps[i] for i = 1 to M-1
		while (i < M) {
			if (searchString.charAt(i) == searchString.charAt(len)) {
				len++;
				lps[i] = len;
				i++;
			} else // (pat[i] != pat[len])
			{
				// This is tricky. Consider the example. AAACAAAA and i = 7. The idea is similar to search step.
				if (len != 0) {
					len = lps[len - 1];
					// Also, note that we do not increment i here
				} else // if (len == 0)
				{
					lps[i] = len;
					i++;
				}
			}
		}
	}

	public static void main(String args[]) {
		String content = "ABABD ABA CDABAB CABAB";
		String searchString = "BD AB";
		matchSearchText(searchString, content);
	}
}
//This code has been contributed by Amit Khandelwal. 
